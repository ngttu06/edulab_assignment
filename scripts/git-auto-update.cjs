#!/usr/bin/env node
/* Explicitly opt-in Git backups; no dependencies, deployment, pull, or force-push. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const {spawnSync} = require('node:child_process');

const root = path.resolve(__dirname, '..');
const env = {...process.env, GIT_TERMINAL_PROMPT:'0', GCM_INTERACTIVE:'Never', GIT_LITERAL_PATHSPECS:'1'};
const allowedRoots = new Set(['.gitignore', '.env.example', '.env.remote.example', 'README.md',
    'pom.xml', 'docker-compose.yml', 'run-local.sh', 'run-remote.sh']);
const secretPatterns = [
    /-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----/,
    /\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b/,
    /\bAKIA[A-Z0-9]{16}\b/,
    /\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}\b/,
    /https?:\/\/[^\s/@:]+:[^\s/@]+@/
];

function git(args, accepted = [0]) {
    const operation = args[0] === '-c' ? args[2] : args[0];
    const result = spawnSync('git', args, {cwd:root, env, encoding:'utf8', timeout:120000, maxBuffer:16 * 1024 * 1024});
    if (result.error) throw new Error(`git ${operation}: ${result.error.message}`);
    if (!accepted.includes(result.status)) {
        throw new Error(`git ${operation} failed (${result.status}):\n${result.stderr || result.stdout}`);
    }
    return result;
}

function isAllowed(file) {
    return allowedRoots.has(file) || file.startsWith('src/') || file.startsWith('scripts/');
}

function isSensitivePath(file) {
    if (file === '.env.example' || file === '.env.remote.example') return false;
    return file.split('/').some(part => /^\.env(?:\.|$)/.test(part)
        || ['.git', '.tools', '.agents', 'node_modules', 'target', 'storage', '.DS_Store'].includes(part))
        || /\.(?:pem|key|p12|pfx|jks|keystore|log|dump)$/i.test(file);
}

function checkContent(file, content) {
    if (secretPatterns.some(pattern => pattern.test(content))) {
        throw new Error(`Possible credential in ${file}; review manually. No secret content is printed.`);
    }
}

function changedFiles() {
    return [...new Set(git(['ls-files', '--modified', '--deleted', '--others', '--exclude-standard', '-z'])
        .stdout.split('\0').filter(Boolean))];
}

function checkState() {
    if (git(['rev-parse', '--show-toplevel']).stdout.trim() !== fs.realpathSync(root)) {
        throw new Error('Run this script from its own repository, not a nested copy.');
    }
    if (git(['symbolic-ref', '--quiet', '--short', 'HEAD']).stdout.trim() !== 'main') {
        throw new Error('Auto-update only runs on main. Switch branches manually.');
    }
    git(['rev-parse', '--verify', 'HEAD']);
    const gitDir = path.resolve(root, git(['rev-parse', '--git-dir']).stdout.trim());
    for (const marker of ['MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'BISECT_LOG', 'rebase-merge', 'rebase-apply', 'sequencer']) {
        if (fs.existsSync(path.join(gitDir, marker))) throw new Error(`Git operation in progress (${marker}); finish it manually.`);
    }
    if (git(['diff', '--cached', '--quiet'], [0, 1]).status !== 0) {
        throw new Error('Index already contains staged changes; commit or unstage them manually first.');
    }
    const files = changedFiles();
    for (const file of files) {
        if (isSensitivePath(file)) throw new Error(`Sensitive/generated path is not eligible: ${file}`);
        if (!isAllowed(file)) continue;
        const absolute = path.join(root, file);
        let stat;
        try { stat = fs.lstatSync(absolute); }
        catch (error) {
            if (error.code === 'ENOENT') continue; // A tracked deletion is eligible too.
            throw error;
        }
        if (!stat.isFile()) throw new Error(`Only regular files may be staged: ${file}`);
        checkContent(file, fs.readFileSync(absolute, 'utf8'));
    }
    return {files:files.filter(isAllowed), ignored:files.filter(file => !isAllowed(file))};
}

function checkRemote() {
    const fetchUrls = git(['remote', 'get-url', '--all', 'origin']).stdout.trim().split('\n');
    const pushUrls = git(['remote', 'get-url', '--push', '--all', 'origin']).stdout.trim().split('\n');
    if (fetchUrls.length !== 1 || pushUrls.length !== 1 || fetchUrls[0] !== pushUrls[0]) {
        throw new Error('origin must have one matching fetch/push URL; review remote configuration.');
    }
    if (/^https?:\/\//.test(fetchUrls[0])) {
        const url = new URL(fetchUrls[0]);
        if (url.username || url.password) throw new Error('Do not put credentials in the remote URL. Use a credential helper.');
    }
    if (git(['config', '--bool', '--get', 'remote.origin.mirror'], [0, 1]).stdout.trim() === 'true') {
        throw new Error('Mirror remotes are not supported.');
    }
    const remote = git(['ls-remote', '--exit-code', '--heads', 'origin', 'refs/heads/main'], [0, 2]);
    if (remote.status === 0) {
        git(['fetch', '--no-tags', 'origin', 'refs/heads/main:refs/remotes/origin/main']);
        if (git(['merge-base', '--is-ancestor', 'refs/remotes/origin/main', 'HEAD'], [0, 1]).status !== 0) {
            throw new Error('origin/main is ahead or diverged. Fetch/review/integrate manually; no automatic pull or force-push.');
        }
    }
}

function sync(options) {
    let selection = checkState();
    if (options.dryRun) {
        console.log('Dry run: no staging, commit, network access, or push.');
        console.log(`Eligible files (${selection.files.length}):\n${selection.files.join('\n')}`);
        console.log(`Outside allowlist (${selection.ignored.length}):\n${selection.ignored.join('\n')}`);
        return;
    }
    checkRemote(); // Do not create repeated offline commits when authentication fails.
    selection = checkState(); // Fetch may have taken time; recheck before touching the index.
    if (selection.files.length) {
        git(['add', '--', ...selection.files]);
        git(['diff', '--cached', '--check']);
        // Inspect staged blobs too, in case a file changed during staging.
        const staged = git(['diff', '--cached', '--name-only', '--diff-filter=ACMRT', '-z']).stdout.split('\0').filter(Boolean);
        for (const file of staged) {
            if (!isAllowed(file) || isSensitivePath(file)) throw new Error(`Unexpected staged path: ${file}`);
            checkContent(file, git(['show', `:${file}`]).stdout);
        }
        if (git(['diff', '--cached', '--quiet'], [0, 1]).status !== 0) {
            console.log(git(['commit', '-m', options.message || `chore: auto-update EduLab (${new Date().toISOString()})`]).stdout.trim());
        }
    }
    // Explicit refspec; no --force, --all, --mirror, or automatic tags.
    const push = git(['-c', 'push.followTags=false', 'push', '--set-upstream', 'origin', 'HEAD:refs/heads/main']);
    console.log((push.stdout + push.stderr).trim());
    console.log(`Synced main at ${git(['rev-parse', '--short', 'HEAD']).stdout.trim()}. Skipped ${selection.ignored.length} out-of-scope files.`);
}

function parseArgs() {
    const options = {watch:false, dryRun:false, interval:60, message:''};
    const args = process.argv.slice(2);
    for (let i = 0; i < args.length; i++) {
        switch (args[i]) {
            case '--once': options.watch = false; break;
            case '--watch': options.watch = true; break;
            case '--dry-run': options.dryRun = true; break;
            case '--interval': options.interval = Number(args[++i]); break;
            case '--message':
                options.message = args[++i];
                if (!options.message || options.message.startsWith('--')) throw new Error('--message requires text.');
                break;
            case '--help':
                console.log('Usage: node scripts/git-auto-update.cjs [--once | --watch] [--interval seconds] [--dry-run] [--message text]');
                return null;
            default: throw new Error(`Unknown option: ${args[i]}`);
        }
    }
    if (!Number.isInteger(options.interval) || options.interval < 5 || options.interval > 86400) {
        throw new Error('--interval must be an integer from 5 to 86400 seconds.');
    }
    if (options.watch && options.dryRun) throw new Error('Use --dry-run without --watch.');
    return options;
}

async function main() {
    const options = parseArgs();
    if (!options) return;
    const commonDir = path.resolve(root, git(['rev-parse', '--git-common-dir']).stdout.trim());
    const lock = path.join(commonDir, 'edulab-auto-update.lock');
    if (!options.dryRun) {
        try { fs.mkdirSync(lock); }
        catch (error) {
            if (error.code === 'EEXIST') throw new Error(`Another watcher may be running. Check ${lock} before removing a stale lock.`);
            throw error;
        }
        const cleanup = () => fs.rmSync(lock, {recursive:true, force:true});
        process.once('exit', cleanup);
        process.once('SIGINT', () => process.exit(130));
        process.once('SIGTERM', () => process.exit(143));
        fs.writeFileSync(path.join(lock, 'pid'), `${process.pid}\n`);
    }
    do {
        sync(options);
        if (options.watch) {
            console.log(`Waiting ${options.interval}s; Ctrl+C to stop. Stop the watcher before manual Git operations.`);
            await new Promise(resolve => setTimeout(resolve, options.interval * 1000));
        }
    } while (options.watch);
}

main().catch(error => {
    console.error(`Auto-update stopped: ${error.message}`);
    console.error('Inspect git status before retrying. Any commit from a failed push is kept locally; no rollback/reset is performed.');
    process.exitCode = 1;
});