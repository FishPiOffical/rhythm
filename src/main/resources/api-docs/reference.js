const version = new URLSearchParams(window.location.search).get('version') === 'v1' ? 'v1' : 'v3'

for (const link of document.querySelectorAll('[data-version]')) {
  if (link.dataset.version === version) link.setAttribute('aria-current', 'page')
  else link.removeAttribute('aria-current')
}

if (window.Scalar) {
  const base = new URL('./api-docs/', window.location.href)
  window.Scalar.createApiReference('#api-reference', {
    url: new URL(version, base).href,
    persistAuth: false,
    hideTestRequestButton: true,
    hideClientButton: true,
    showDeveloperTools: 'never',
    withDefaultFonts: false,
  })
}
