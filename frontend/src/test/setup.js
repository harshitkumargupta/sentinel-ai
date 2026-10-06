import '@testing-library/jest-dom';

// jsdom has no matchMedia; stub it so ThemeProvider/reduced-motion checks work in tests.
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false, media: query, onchange: null,
    addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {}, dispatchEvent() { return false; },
  });
}

// jsdom has no IntersectionObserver (used by useCanvasActive).
if (!window.IntersectionObserver) {
  window.IntersectionObserver = class {
    observe() {} unobserve() {} disconnect() {}
  };
}
