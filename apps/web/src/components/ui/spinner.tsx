import OrbitSpinner from '@/components/astra/OrbitSpinner'

interface SpinnerProps {
  size?:      number
  className?: string
}

export function Spinner({ size = 16, className }: SpinnerProps) {
  return <OrbitSpinner size={size} className={className} />
}
