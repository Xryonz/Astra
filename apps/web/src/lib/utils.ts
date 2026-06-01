import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Merge utility usada pelo ShadcnUI: combina classes condicionais (clsx)
 * com dedupe de classes conflitantes do Tailwind (twMerge).
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
