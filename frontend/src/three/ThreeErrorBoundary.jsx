import { Component } from 'react';

/**
 * Error boundary around every 3D component. A WebGL/renderer failure renders the provided 2D/static
 * fallback instead of crashing the page. Logs once for diagnostics.
 */
export default class ThreeErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { failed: false };
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error) {
    // eslint-disable-next-line no-console
    console.warn('[3D] rendering failed, using fallback:', error?.message || error);
  }

  render() {
    if (this.state.failed) return this.props.fallback || null;
    return this.props.children;
  }
}
