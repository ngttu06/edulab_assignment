'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {spawn, spawnSync} = require('node:child_process');

// Every test uses disposable repositories and a local bare remote, never GitHub.
function fixture(t) {
    const base = fs.mkdtempSync(path.join(os.tmpdir(), 'edulab-git-test-'));
    const repo = path.join(base, 'working copy');
    const remote = path.join(base, 'remote.git');
    fs.mkdirSync(repo);
    const env = {...process.env, GIT_CONFIG_GLOBAL:os.devNull, GIT_CONFIG_NOSYSTEM:'1', GIT_TERMINAL_PROMPT:'0'};
    for (const key of ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_COMMON_DIR']) delete env[key];
    function git(args, cwd = repo) {
        const result = spawnSync('git', args, {cwd, env, encoding:'utf8', timeout:10000});
        assert.equal(result.status, 0, result.stderr);
        return result.stdout.trim();
    }
    function write(file, content) {
        fs.mkdirSync(path.dirname(path.join(repo, file)), {recursive:true});
        fs.writeFileSync(path.join(repo, file), content);
    }
    git(['init', '--bare', remote], base);
    git(['init', '-b', 'main']);
    git(['config', 'user.name', 'EduLab Git Test']);
    git(['config', 'user.email', 'test@example.invalid']);
    write('README.md', 'Initial\n');
    write('scripts/git-auto-update.cjs', fs.readFileSync(path.join(__dirname, 'git-auto-update.cjs')));
    write('.gitignore', fs.readFileSync(path.join(__dirname, '..', '.gitignore')));
    git(['add', '.']);
    git(['commit', '-m', 'Initial']);
    git(['remote', 'add', 'origin', remote]);
    const script = path.join(repo, 'scripts/git-auto-update.cjs');
    function run(args = []) {
        return spawnSync(process.execPath, [script, ...args], {cwd:base, env, encoding:'utf8', timeout:15000});
    }
    function fails(args, expression) {
        const result = run(args);
        assert.equal(result.status, 1, result.stdout);
        assert.match(result.stderr, expression);
        return result;
    }
    t.after(() => fs.rmSync(base, {recursive:true, force:true}));
    return {base, repo, remote, env, script, git, write, run, fails};
}

test('commits/pushes eligible changes, ignores local artifacts, and is idempotent', t => {
    const f = fixture(t);
    for (const file of ['.env.local', '.tools/shot.png', 'target/app.war', '.agents/local.md',
        'storage/file.pdf', 'Mapstudy.edu.vn - reference.html', 'Mapstudy.edu.vn - reference_files/a.js']) {
        f.write(file, 'Private fixture\n');
    }
    f.write('notes.txt', 'Out of scope\n');
    f.write('src/file with spaces.txt', 'Hello\n');
    f.write('README.md', 'Updated\n');
    const result = f.run(['--message', 'feat: test sync']);
    assert.equal(result.status, 0, result.stderr);
    assert.equal(f.git(['log', '-1', '--format=%s']), 'feat: test sync');
    assert.equal(f.git(['rev-parse', 'HEAD']), f.git(['rev-parse', 'refs/heads/main'], f.remote));
    const tracked = f.git(['ls-files']);
    assert.match(tracked, /src\/file with spaces.txt/);
    assert.doesNotMatch(tracked, /notes|\.env.local|\.tools|target|storage|Mapstudy|\.agents/);
    const head = f.git(['rev-parse', 'HEAD']);
    assert.equal(f.run().status, 0);
    assert.equal(f.git(['rev-parse', 'HEAD']), head);
    fs.unlinkSync(path.join(f.repo, 'src/file with spaces.txt'));
    assert.equal(f.run().status, 0);
    assert.doesNotMatch(f.git(['ls-files']), /file with spaces/);
});

test('dry run changes no files or refs and does not need an accessible remote', t => {
    const f = fixture(t);
    f.git(['remote', 'remove', 'origin']);
    f.write('src/new.txt', 'New\n');
    const before = f.git(['status', '--porcelain=v1']);
    const result = f.run(['--dry-run']);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /src\/new.txt/);
    assert.equal(f.git(['status', '--porcelain=v1']), before);
    assert.equal(fs.existsSync(path.join(f.repo, '.git/edulab-auto-update.lock')), false);
});

test('preserves manually staged changes', t => {
    const f = fixture(t);
    f.write('README.md', 'Manual\n');
    f.git(['add', 'README.md']);
    const diff = f.git(['diff', '--cached']);
    f.fails([], /Index already contains/);
    assert.equal(f.git(['diff', '--cached']), diff);
});

test('refuses a different branch and an in-progress merge', t => {
    const f = fixture(t);
    f.git(['checkout', '-b', 'feature']);
    f.fails([], /only runs on main/);
    f.git(['checkout', 'main']);
    f.write('.git/MERGE_HEAD', `${f.git(['rev-parse', 'HEAD'])}\n`);
    f.fails([], /operation in progress/);
});

test('refuses remote-ahead and diverged histories without changing HEAD or index', t => {
    const f = fixture(t);
    assert.equal(f.run().status, 0);
    const original = f.git(['rev-parse', 'HEAD']);
    const other = path.join(f.base, 'other');
    f.git(['clone', '--branch', 'main', f.remote, other], f.base);
    f.git(['config', 'user.name', 'Other'], other);
    f.git(['config', 'user.email', 'other@example.invalid'], other);
    fs.writeFileSync(path.join(other, 'README.md'), 'Remote change\n');
    f.git(['commit', '-am', 'Remote commit'], other);
    f.git(['push', 'origin', 'main'], other);
    f.write('src/local.txt', 'Uncommitted\n');
    f.fails([], /ahead or diverged/);
    assert.equal(f.git(['rev-parse', 'HEAD']), original);
    assert.equal(f.git(['diff', '--cached']), '');
    f.git(['add', 'src/local.txt']);
    f.git(['commit', '-m', 'Local diverged commit']);
    const diverged = f.git(['rev-parse', 'HEAD']);
    f.fails([], /ahead or diverged/);
    assert.equal(f.git(['rev-parse', 'HEAD']), diverged);
});

test('blocks common secret content without printing it', t => {
    const f = fixture(t);
    const secret = ['ghp_', 'A'.repeat(36)].join('');
    f.write('src/config.txt', secret);
    const result = f.fails([], /Possible credential/);
    assert.ok(!result.stderr.includes(secret));
    assert.equal(f.git(['diff', '--cached']), '');
});

test('blocks tracked sensitive paths even if ignored by Git', t => {
    const f = fixture(t);
    f.write('.env.local', 'Fixture only\n');
    f.git(['add', '-f', '.env.local']);
    f.git(['commit', '-m', 'Bad fixture']);
    f.write('.env.local', 'Changed fixture\n');
    f.fails([], /Sensitive\/generated path/);
});

test('refuses symlinks, including dangling symlinks', t => {
    const f = fixture(t);
    fs.mkdirSync(path.join(f.repo, 'src'));
    fs.symlinkSync('../README.md', path.join(f.repo, 'src/link'));
    f.fails([], /Only regular files/);
    fs.unlinkSync(path.join(f.repo, 'src/link'));
    fs.symlinkSync('../missing.txt', path.join(f.repo, 'src/link'));
    f.fails([], /Only regular files/);
});

test('rejects concurrent watcher locks and invalid options', t => {
    const f = fixture(t);
    const lock = path.join(f.repo, '.git/edulab-auto-update.lock');
    fs.mkdirSync(lock);
    f.fails([], /Another watcher/);
    assert.ok(fs.existsSync(lock));
    f.fails(['--interval', '0'], /--interval/);
    f.fails(['--message'], /--message/);
    f.fails(['--watch', '--dry-run'], /without --watch/);
});

test('unreachable remote does not stage or create a commit', t => {
    const f = fixture(t);
    f.git(['remote', 'set-url', 'origin', path.join(f.base, 'missing.git')]);
    f.write('README.md', 'Offline\n');
    const head = f.git(['rev-parse', 'HEAD']);
    f.fails([], /ls-remote failed/);
    assert.equal(f.git(['rev-parse', 'HEAD']), head);
    assert.equal(f.git(['diff', '--cached']), '');
});

test('rejects mismatched push URLs', t => {
    const f = fixture(t);
    f.git(['remote', 'set-url', '--push', 'origin', path.join(f.base, 'elsewhere.git')]);
    f.fails([], /matching fetch\/push URL/);
});

test('keeps local commit on rejected push, then retries without a duplicate commit', t => {
    const f = fixture(t);
    assert.equal(f.run().status, 0);
    const hook = path.join(f.remote, 'hooks/pre-receive');
    fs.writeFileSync(hook, '#!/bin/sh\nexit 1\n', {mode:0o755});
    f.write('README.md', 'Rejected change\n');
    f.fails([], /push failed/);
    const head = f.git(['rev-parse', 'HEAD']);
    assert.notEqual(f.git(['rev-parse', 'refs/heads/main'], f.remote), head);
    fs.unlinkSync(hook);
    assert.equal(f.run().status, 0);
    assert.equal(f.git(['rev-parse', 'HEAD']), head);
    assert.equal(f.git(['rev-parse', 'refs/heads/main'], f.remote), head);
});

test('watch mode syncs subsequent edits and cleans its lock on SIGTERM', {timeout:20000}, async t => {
    const f = fixture(t);
    let output = '';
    const child = spawn(process.execPath, [f.script, '--watch', '--interval', '5'], {cwd:f.base, env:f.env});
    const closed = new Promise(resolve => child.once('close', resolve));
    child.stdout.on('data', data => { output += data; });
    child.stderr.on('data', data => { output += data; });
    async function waitFor(check) {
        const deadline = Date.now() + 12000;
        while (!check()) {
            assert.equal(child.exitCode, null, output);
            assert.ok(Date.now() < deadline, output);
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }
    try {
        await waitFor(() => output.includes('Waiting 5s'));
        f.write('README.md', 'Watched edit\n');
        await waitFor(() => (output.match(/Synced main/g) || []).length >= 2);
        assert.equal(f.git(['show', 'main:README.md'], f.remote), 'Watched edit');
        child.kill('SIGTERM');
        await closed;
        assert.equal(fs.existsSync(path.join(f.repo, '.git/edulab-auto-update.lock')), false);
    } finally {
        if (child.exitCode === null) child.kill('SIGTERM');
        await closed;
    }
});