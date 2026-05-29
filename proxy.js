const net = require('net');
const dgram = require('dgram');
const zlib = require('zlib');

const L_PORT = 25566;
const B_PORT = 25565;
const B_HOST = '127.0.0.1';

function rv(b, o) {
    let v = 0, s = 0, i = o;
    while (true) {
        let x = b[i++];
        v |= (x & 0x7F) << s;
        if (!(x & 0x80)) break;
        s += 7;
    }
    return { v, n: i - o };
}

function wv(v) {
    const b = [];
    while (v >= 0x80) {
        b.push((v & 0x7F) | 0x80);
        v >>= 7;
    }
    b.push(v);
    return Buffer.from(b);
}

const server = net.createServer((c) => {
    const b = net.connect(B_PORT, B_HOST);
    let thr = -1, st = 0, cBuf = Buffer.alloc(0), bBuf = Buffer.alloc(0);

    const proc = (src, dst, isC) => {
        let buf = isC ? cBuf : bBuf;
        buf = Buffer.concat([buf, src]);
        let o = 0;
        while (o < buf.length) {
            try {
                const { v: l, n: ln } = rv(buf, o);
                if (o + ln + l > buf.length) break;
                let p = buf.slice(o + ln, o + ln + l);
                o += ln + l;

                let rawP = p;
                if (thr >= 0) {
                    const { v: ul, n: un } = rv(p, 0);
                    if (ul > 0) p = zlib.inflateSync(p.slice(un));
                    else p = p.slice(un);
                }

                const { v: id, n: idn } = rv(p, 0);
                const data = p.slice(idn);

                if (isC) {
                    if (st === 0 && id === 0x00) {
                        let po = 0;
                        const { n: vn } = rv(data, po); po += vn;
                        const { v: sl, n: sn } = rv(data, po); po += sn + sl;
                        po += 2;
                        st = rv(data, po).v;
                    } else if (st >= 3 && (id === 0x04 || id === 0x03)) {
                        const { v: cl, n: cn } = rv(data, 0);
                        const cmd = data.slice(cn, cn + cl).toString();
                        if (cmd === 'server' || cmd === '/server') {
                            const msg = JSON.stringify({ text: "§a[Proxy] §fOK" });
                            const mPk = Buffer.concat([wv(Buffer.byteLength(msg)), Buffer.from(msg), Buffer.alloc(1, 0), Buffer.alloc(16, 0)]);
                            let fPk = Buffer.concat([wv(0x6C), mPk]);
                            if (thr >= 0) fPk = Buffer.concat([wv(fPk.length), zlib.deflateSync(fPk)]);
                            c.write(Buffer.concat([wv(fPk.length), fPk]));
                            continue;
                        }
                    }
                } else {
                    if (st === 2) {
                        if (id === 0x03) thr = rv(data, 0).v;
                        else if (id === 0x02) st = 3;
                    } else if (st === 3 && id === 0x02) st = 4;
                }
                dst.write(Buffer.concat([wv(rawP.length), rawP]));
            } catch (e) { break; }
        }
        if (isC) cBuf = buf.slice(o); else bBuf = buf.slice(o);
    };

    c.on('data', (d) => proc(d, b, true));
    b.on('data', (d) => proc(d, c, false));
    c.on('error', () => { c.destroy(); b.destroy(); });
    b.on('error', () => { c.destroy(); b.destroy(); });
});

const udp = dgram.createSocket('udp4');
const uCl = new Map();
udp.on('message', (m, r) => {
    const k = `${r.address}:${r.port}`;
    let s = uCl.get(k);
    if (!s) {
        s = dgram.createSocket('udp4');
        s.on('message', (rm) => udp.send(m, r.port, r.address));
        uCl.set(k, s);
    }
    s.send(m, B_PORT, B_HOST);
});

server.listen(L_PORT, '0.0.0.0', () => console.log(`Proxy V2 Active: ${L_PORT}`));
udp.bind(L_PORT);
