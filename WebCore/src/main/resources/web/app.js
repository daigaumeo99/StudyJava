const state = document.querySelector('#server-state');
async function checkServer() {
  try {
    const response = await fetch('/api/status', { signal: AbortSignal.timeout(8000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    state.textContent = data.status === 'online' ? 'Online' : 'Không xác định';
    document.querySelector('#server-detail').textContent = data.server;
  } catch {
    state.textContent = 'Mất kết nối';
    document.querySelector('#server-detail').textContent = 'Không thể kết nối đến server';
  }
}
checkServer();
setInterval(checkServer, 30000);

document.querySelector('#echo-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const button = event.currentTarget.querySelector('button');
  const status = document.querySelector('#request-state');
  const output = document.querySelector('#response');
  button.disabled = true;
  status.textContent = 'Đang gửi…';
  try {
    const response = await fetch('/api/echo', {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: document.querySelector('#message').value,
      signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}: ${await response.text()}`);
    output.textContent = JSON.stringify(await response.json(), null, 2);
    status.textContent = `${response.status} OK`;
  } catch (error) {
    output.textContent = `Không gửi được request: ${error.message}`;
    status.textContent = 'Có lỗi';
  } finally {
    button.disabled = false;
  }
});
