
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { sentry } from '@/lib/sentry'

interface Props { children: ReactNode }
interface State { hasError: boolean; error: Error | null }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    sentry.captureException(error)
    console.error('[ErrorBoundary]', error, info.componentStack)
  }

  reset = () => this.setState({ hasError: false, error: null })

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children

    return (
      <div className="min-h-screen flex items-center justify-center px-6 bg-background text-foreground">
        <div className="max-w-md w-full">
          <div className="size-12 bg-card border border-border rounded-2xl flex items-center justify-center mb-5">
            <span className="text-2xl text-primary">✦</span>
          </div>
          <h1 className="text-2xl font-normal mb-2" style={{ fontFamily: 'var(--font-display)' }}>
            Algo <em className="text-primary">interrompeu</em>
          </h1>
          <p className="text-sm text-muted-foreground mb-6 leading-relaxed">
            Um erro inesperado quebrou o render. Você pode tentar recarregar — se persistir,
            já registramos pra investigar.
          </p>
          {this.state.error?.message && (
            <pre className="text-xs text-muted-foreground bg-card border border-border rounded-lg p-3 mb-6 overflow-x-auto">
              {this.state.error.message}
            </pre>
          )}
          <div className="flex gap-2">
            <button
              onClick={this.reset}
              className="px-4 py-2 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:opacity-90 transition-opacity"
            >
              Tentar de novo
            </button>
            <button
              onClick={() => window.location.reload()}
              className="px-4 py-2 rounded-lg border border-border text-sm font-medium hover:bg-card transition-colors"
            >
              Recarregar página
            </button>
          </div>
        </div>
      </div>
    )
  }
}
