import { Component } from 'react';

/** Global error boundary so a render error shows a friendly message instead of a blank page. */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('UI error boundary caught:', error, info);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="auth-shell">
          <div className="auth-card">
            <h2>Something went wrong</h2>
            <p className="subtitle">The page hit an unexpected error. Try reloading.</p>
            <button onClick={() => window.location.reload()}>Reload</button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
