/* Captura manual da PoC. Sem chave, upload, analytics ou chamadas ao backend. */
'use strict';
const el = (id) => document.getElementById(id);
let recorder, stream, chunks, started, timer, hardStop, blobUrl, metadataUrl, file;
let busy = false;
const supported = window.isSecureContext && navigator.mediaDevices?.getUserMedia && window.MediaRecorder;
const status = (message) => { el('status').textContent = message; };
for (const option of el('format').options) {
  if (option.value) option.disabled = !window.MediaRecorder?.isTypeSupported(option.value);
}
if (!supported) {
  status('Abra esta página em HTTPS ou em localhost e use um navegador com gravação de áudio.');
} else {
  el('start').disabled = false;
  status('Pronto. Permita o microfone e grave a frase. Limite: 30 segundos / 2 MiB.');
}
function releaseMicrophone() {
  stream?.getTracks().forEach((track) => track.stop());
  clearInterval(timer);
  clearTimeout(hardStop);
}
function stopRecording() {
  if (recorder?.state === 'recording') recorder.stop();
  el('stop').disabled = true;
}
el('start').addEventListener('click', async () => {
  if (busy) return;
  busy = true;
  el('start').disabled = true;
  el('format').disabled = true;
  el('result').hidden = true;
  el('playback').hidden = true;
  el('playback').pause();
  el('playback').removeAttribute('src');
  if (blobUrl) URL.revokeObjectURL(blobUrl);
  if (metadataUrl) URL.revokeObjectURL(metadataUrl);
  file = undefined;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio:true, video:false });
    const mime = el('format').value;
    recorder = new MediaRecorder(stream, mime ? { mimeType:mime } : {});
    chunks = [];
    let bytes = 0;
    let captureFailed = false;
    recorder.addEventListener('dataavailable', (event) => {
      if (event.data.size) { chunks.push(event.data); bytes += event.data.size; }
      if (bytes > 2 * 1024 * 1024) stopRecording();
    });
    recorder.addEventListener('error', () => {
      captureFailed = true;
      releaseMicrophone();
      status('O navegador interrompeu a gravação. Tente novamente.');
      if (recorder.state === 'recording') recorder.stop();
      else { busy = false; el('start').disabled = false; el('format').disabled = false; }
    });
    recorder.addEventListener('stop', () => {
      const duration = (performance.now() - started) / 1000;
      releaseMicrophone();
      busy = false;
      el('start').disabled = false;
      el('stop').disabled = true;
      el('format').disabled = false;
      if (captureFailed) return;
      const mimeType = recorder.mimeType || chunks[0]?.type || '';
      const blob = new Blob(chunks, { type:mimeType });
      if (!blob.size || blob.size > 2 * 1024 * 1024 || duration > 31) {
        status('Gravação vazia ou acima do limite. Grave novamente, parando antes de 30 segundos.');
        return;
      }
      const extension = mimeType.includes('webm') ? 'webm' : mimeType.includes('mp4') ? 'm4a' : 'bin';
      const name = `fin-voz-${new Date().toISOString().replace(/[:.]/g,'-')}`;
      file = new File([blob], `${name}.${extension}`, { type:mimeType });
      blobUrl = URL.createObjectURL(blob);
      el('playback').src = blobUrl;
      el('playback').hidden = false;
      el('download').href = blobUrl;
      el('download').download = file.name;
      const report = { sample:'voice-poc-v1', mimeType, bytes:blob.size,
        captureSeconds:Math.round(duration * 1000) / 1000, createdAt:new Date().toISOString(),
        browser:navigator.userAgent, note:'Duração do relógio da captura, não validação do contêiner.' };
      const json = JSON.stringify(report,null,2);
      metadataUrl = URL.createObjectURL(new Blob([json],{ type:'application/json' }));
      el('metadata').href = metadataUrl;
      el('metadata').download = `${name}.json`;
      el('details').textContent = json;
      el('share').hidden = !(navigator.canShare && navigator.canShare({ files:[file] }));
      el('result').hidden = false;
      status('Gravação pronta. Ouça e salve o áudio original e o relatório.');
    }, { once:true });
    recorder.start(500);
    started = performance.now();
    el('stop').disabled = false;
    status('Gravando… 0 s');
    timer = setInterval(() => status(`Gravando… ${Math.floor((performance.now()-started)/1000)} s`), 500);
    hardStop = setTimeout(stopRecording, 29_000);
  } catch (error) {
    releaseMicrophone();
    busy = false;
    el('start').disabled = false;
    el('stop').disabled = true;
    el('format').disabled = false;
    status(error.name === 'NotAllowedError' ? 'Microfone bloqueado. Permita o acesso e tente novamente.'
      : 'Não foi possível gravar. Confira o microfone e tente outro formato.');
  }
});
el('stop').addEventListener('click', stopRecording);
el('share').addEventListener('click', async () => {
  if (!file) return;
  try { await navigator.share({ files:[file], title:'FIN · áudio fictício de teste' }); }
  catch (error) { if (error.name !== 'AbortError') status('Use o link Salvar áudio original.'); }
});
document.addEventListener('visibilitychange', () => { if (document.hidden) stopRecording(); });
window.addEventListener('pagehide', () => { stopRecording(); releaseMicrophone(); });
