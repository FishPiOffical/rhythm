function safeRequestPath(value) {
  try {
    const url = new URL(value);
    return `${url.origin}${url.pathname}`;
  } catch {
    return '(未知路径)';
  }
}

function safeInitiatorFrames(initiator) {
  return (initiator?.stack?.callFrames || []).slice(0, 4).map(frame =>
    `${frame.url ? safeRequestPath(frame.url) : '(inline)'}:${Number(frame.lineNumber || 0) + 1}`);
}

export function trackUnauthorizedResponses(client) {
  client.apiRequests = new Map();
  client.unauthorizedResponseSequence = 0;
  client.unauthorizedResponses = [];
  client.on('Network.requestWillBeSent', event => {
    if (event.type !== 'XHR' && event.type !== 'Fetch') return;
    client.apiRequests.set(event.requestId, {
      type: event.type,
      method: event.request?.method || 'UNKNOWN',
      url: safeRequestPath(event.request?.url),
      initiator: safeInitiatorFrames(event.initiator),
    });
  });
  client.on('Network.responseReceived', event => {
    if ((event.type !== 'XHR' && event.type !== 'Fetch') || Number(event.response?.status) !== 401) return;
    const request = client.apiRequests.get(event.requestId) || {
      type: event.type,
      method: 'UNKNOWN',
      url: safeRequestPath(event.response?.url),
      initiator: [],
    };
    client.unauthorizedResponses.push({ ...request, sequence: ++client.unauthorizedResponseSequence });
    if (client.unauthorizedResponses.length > 50) client.unauthorizedResponses.shift();
  });
  const clearRequest = event => client.apiRequests.delete(event.requestId);
  client.on('Network.loadingFinished', clearRequest);
  client.on('Network.loadingFailed', clearRequest);
}

export function beginUnauthorizedCapture(client) {
  return client.unauthorizedResponseSequence;
}

export function getUnauthorizedResponses(client, afterSequence) {
  return client.unauthorizedResponses.filter(item => item.sequence > afterSequence);
}
