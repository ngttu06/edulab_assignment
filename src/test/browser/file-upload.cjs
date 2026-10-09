/* Dependency-free unit regression for direct XHR uploads. No network or credentials. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const requests = [];
class Xhr {
    constructor() { this.upload = {}; this.headers = {}; requests.push(this); }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(body) { this.body = body; if (this.throwOnSend) throw new Error('send failed'); }
    abort() { this.onabort?.(); }
}
const window = {location: {origin: 'http://127.0.0.1:8088'}};
vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/file-upload.js'), 'utf8'), {window, XMLHttpRequest: Xhr});

function signal(aborted = false) {
    const listeners = new Set();
    return {
        aborted, listeners,
        addEventListener(name, listener, options) {
            assert.equal(name, 'abort'); assert.equal(options.once, true); listeners.add(listener);
        },
        removeEventListener(name, listener) { assert.equal(name, 'abort'); listeners.delete(listener); },
        abort() { this.aborted = true; for (const listener of [...listeners]) listener(); }
    };
}
function cleaned(xhr, controller) {
    assert.equal(controller.listeners.size, 0, 'Abort listener must be removed');
    for (const name of ['onload', 'onerror', 'ontimeout', 'onabort']) assert.equal(xhr[name], null);
    assert.equal(xhr.upload.onprogress, null);
}
async function main() {
    const controller = signal(), progress = [];
    const start = () => window.edulabUploads.put('https://storage.test/object', 'raw-file', {'Content-Type': 'image/jpeg'}, value => progress.push(value), controller);
    for (let i = 0; i < 12; i++) {
        const upload = start(), xhr = requests.at(-1);
        assert.equal(xhr.method, 'PUT'); assert.equal(xhr.withCredentials, false);
        assert.deepEqual(xhr.headers, {'Content-Type': 'image/jpeg'});
        assert.equal(xhr.body, 'raw-file'); assert.equal(xhr.timeout, 180000);
        xhr.upload.onprogress({lengthComputable: true, loaded: 1, total: 2});
        xhr.status = 200; xhr.onload(); await upload; cleaned(xhr, controller);
    }
    assert.deepEqual(progress, Array(12).fill(50));
    for (const [event, message] of [['onerror', /http:\/\/127\.0\.0\.1:8088.*PUT.*Content-Type/], ['ontimeout', /hết thời gian/], ['onabort', /hủy upload/]]) {
        const upload = start(), xhr = requests.at(-1), rejection = assert.rejects(upload, message);
        xhr[event](); await rejection; cleaned(xhr, controller);
    }
    const denied = start(), xhr = requests.at(-1), denial = assert.rejects(denied, /R2 từ chối upload/);
    xhr.status = 403; xhr.onload(); await denial; cleaned(xhr, controller);
    const aborted = signal(true);
    await assert.rejects(window.edulabUploads.put('https://storage.test/object', 'raw-file', {}, () => {}, aborted), /hủy upload/);
    assert.equal(requests.at(-1).body, undefined); cleaned(requests.at(-1), aborted);
    const active = signal(), upload = window.edulabUploads.put('https://storage.test/object', 'raw-file', {}, () => {}, active);
    const rejection = assert.rejects(upload, /hủy upload/); active.abort(); await rejection; cleaned(requests.at(-1), active);
    Xhr.prototype.throwOnSend = true;
    await assert.rejects(start(), /Không thể bắt đầu upload/); cleaned(requests.at(-1), controller);
    console.log('PASS: repeated direct PUT, progress, CORS origin guidance, HTTP/network/timeout/abort/send errors and listener cleanup.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });