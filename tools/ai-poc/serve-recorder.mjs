import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';

// Rotas fixas: nunca servir a raiz do repositório, .env, classes ou arquivos de áudio.
const routes = new Map([
  ['/', [new URL('../../src/test/resources/ai-poc/recorder.html', import.meta.url), 'text/html; charset=utf-8']],
  ['/recorder.js', [new URL('../../src/test/resources/ai-poc/recorder.js', import.meta.url), 'text/javascript; charset=utf-8']],
]);
const server = createServer(async (request, response) => {
  response.setHeader('Cache-Control', 'no-store');
  response.setHeader('X-Content-Type-Options', 'nosniff');
  response.setHeader('Referrer-Policy', 'no-referrer');
  response.setHeader('Permissions-Policy', 'microphone=(self), camera=()');
  response.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; media-src blob:; connect-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'");
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
  let route;
  try { route = routes.get(new URL(request.url, 'http://localhost').pathname); }
  catch { response.writeHead(400); response.end(); return; }
  if (!route) { response.writeHead(404); response.end(); return; }
  try {
    const data = await readFile(route[0]);
    response.writeHead(200, { 'Content-Type':route[1], 'Content-Length':data.length });
    response.end(request.method === 'HEAD' ? undefined : data);
  } catch { response.writeHead(500); response.end(); }
});
server.listen(8765, '127.0.0.1', () => console.log('Captura de teste: http://localhost:8765'));
