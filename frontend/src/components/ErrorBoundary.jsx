import React from 'react'

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error }
  }

  componentDidCatch(error, errorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo)
  }

  handleRetry = () => {
    sessionStorage.removeItem('last_chunk_reload')
    window.location.reload(true)
  }

  render() {
    if (this.state.hasError) {
      const isChunkError = 
        this.state.error?.name === 'ChunkLoadError' || 
        this.state.error?.message?.includes('Loading chunk') || 
        this.state.error?.message?.includes('dynamic import') ||
        this.state.error?.message?.includes('Failed to fetch dynamically imported module')

      if (isChunkError) {
        const lastReload = sessionStorage.getItem('last_chunk_reload')
        const now = Date.now()
        // Prevent infinite reload loops by limiting to once every 10 seconds
        if (!lastReload || now - parseInt(lastReload, 10) > 10000) {
          sessionStorage.setItem('last_chunk_reload', now.toString())
          window.location.reload(true)
          return (
            <div className="min-h-screen bg-slate-900 text-white flex flex-col items-center justify-center p-6 text-center">
              <div className="animate-spin rounded-full h-10 w-10 border-t-2 border-emerald-500 border-r-2 border-transparent"></div>
              <p className="mt-4 text-slate-400">Updating application...</p>
            </div>
          )
        }
      }

      // Premium Fallback UI for non-recoverable or persistent errors
      return (
        <div className="min-h-screen bg-slate-900 text-white flex flex-col items-center justify-center p-6 text-center">
          <div className="bg-slate-800 p-8 rounded-2xl shadow-xl max-w-md w-full border border-slate-700/50 animate-fade-in">
            <div className="w-16 h-16 bg-amber-500/10 border border-amber-500/20 text-amber-500 rounded-full flex items-center justify-center mx-auto mb-4">
              <svg className="w-8 h-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h2 className="text-xl font-bold mb-2">App Update / Connection Issue</h2>
            <p className="text-slate-400 text-sm mb-6">
              The application was updated or has a connection issue. Please click below to refresh the app.
            </p>
            <button
              onClick={this.handleRetry}
              className="w-full py-3 px-4 bg-emerald-600 hover:bg-emerald-500 text-white font-medium rounded-lg transition-all duration-200 shadow-lg shadow-emerald-600/20 active:scale-95 cursor-pointer"
            >
              Refresh Application
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
