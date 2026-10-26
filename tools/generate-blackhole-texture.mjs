// Original deterministic material data; no external image inputs or packages.
import { writeFileSync, mkdirSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
const size = 512;
function hash(x, y, seed) {
    let v = Math.imul(x, 374761393) ^ Math.imul(y, 668265263) ^ seed;
    v = Math.imul(v ^ (v >>> 13), 1274126177);
    return ((v ^ (v >>> 16)) >>> 0) / 4294967295;
}
const mix = (a, b, t) => a + (b - a) * t;
function noise(x, y, period, seed) {
    x *= period; y *= period;
    const ix = Math.floor(x), iy = Math.floor(y);
    const u = x - ix, v = y - iy, sx = u * u * (3 - 2 * u), sy = v * v * (3 - 2 * v);
    const at = (a, b) => hash(((a % period) + period) % period, ((b % period) + period) % period, seed);
    return mix(mix(at(ix, iy), at(ix + 1, iy), sx), mix(at(ix, iy + 1), at(ix + 1, iy + 1), sx), sy);
}
const raw = Buffer.alloc(size * (size * 4 + 1));
for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const u = x / size, v = y / size;
    let cloud = 0, ridge = 0, weight = 0.52, sum = 0;
    for (let octave = 0; octave < 6; octave++) {
        const period = 4 << octave;
        cloud += noise(u, v, period, 211 + octave * 853) * weight;
        ridge += (1 - Math.abs(2 * noise(u, v, period, 911 + octave * 313) - 1)) * weight;
        sum += weight; weight *= 0.53;
    }
    const grain = noise(u, v, 128, 18427) * 0.6 + noise(u, v, 256, 95731) * 0.4;
    const offset = y * (size * 4 + 1) + 1 + x * 4;
    raw[offset] = Math.round(cloud / sum * 255);
    raw[offset + 1] = Math.round(ridge / sum * 255);
    raw[offset + 2] = Math.round(grain * 255); raw[offset + 3] = 255;
}
function crc32(bytes) {
    let crc = 0xffffffff;
    for (const byte of bytes) {
        crc ^= byte;
        for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
    return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, payload) {
    const bytes = Buffer.concat([Buffer.from(type), payload]), head = Buffer.alloc(4), tail = Buffer.alloc(4);
    head.writeUInt32BE(payload.length); tail.writeUInt32BE(crc32(bytes));
    return Buffer.concat([head, bytes, tail]);
}
const header = Buffer.alloc(13);
header.writeUInt32BE(size, 0); header.writeUInt32BE(size, 4); header[8] = 8; header[9] = 6;
const output = new URL('../src/main/resources/assets/overheaven_foliage_compat/textures/blackhole/disk_detail.png', import.meta.url);
mkdirSync(new URL('.', output), { recursive: true });
writeFileSync(output, Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]));
console.log(fileURLToPath(output));
