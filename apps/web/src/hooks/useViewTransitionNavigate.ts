
import { useNavigate } from 'react-router-dom'

type Navigate = ReturnType<typeof useNavigate>

export function useViewTransitionNavigate(): Navigate {
  return useNavigate()
}
