import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { RegisterSchema, type RegisterInput } from '@astra/types'

export default function RegisterForm() {
  const { t } = useTranslation()
  const { register: registerUser } = useAuth()
  const [serverError, setServerError] = useState<string | null>(null)
  const [searchParams] = useSearchParams()

  // Pré-preencher email + lock se veio do flow "Google → email não registrado"
  const prefilledEmail = searchParams.get('email') ?? ''
  const fromGoogle     = searchParams.get('from') === 'google'

  const form = useForm<RegisterInput>({
    resolver: zodResolver(RegisterSchema),
    defaultValues: { displayName: '', username: '', email: prefilledEmail, password: '' },
  })

  const onSubmit = async (data: RegisterInput) => {
    setServerError(null)
    try {
      await registerUser(data)
    } catch (err: any) {
      setServerError(err.response?.data?.error ?? t('auth.registerError'))
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-3">
        <FormField
          control={form.control}
          name="displayName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('auth.displayName')}</FormLabel>
              <FormControl>
                <Input placeholder={t('auth.displayNamePh')} autoComplete="name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="username"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('auth.username')}</FormLabel>
              <FormControl>
                <Input placeholder={t('auth.usernamePh')} autoComplete="username" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="email"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                {t('auth.email')}
                {fromGoogle && (
                  <span className="ml-2 text-[10px] font-mono uppercase tracking-wider text-(--accent)">
                    {t('auth.fromGoogle')}
                  </span>
                )}
              </FormLabel>
              <FormControl>
                <Input
                  type="email"
                  placeholder={t('auth.emailPh')}
                  autoComplete="email"
                  readOnly={fromGoogle}
                  className={fromGoogle ? 'opacity-80 cursor-not-allowed' : undefined}
                  {...field}
                />
              </FormControl>
              {fromGoogle && (
                <p className="text-marg text-(--text-3) m-0 mt-1 italic">
                  {t('auth.googleEmailNote')}
                </p>
              )}
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="password"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t('auth.password')}</FormLabel>
              <FormControl>
                <Input type="password" placeholder={t('auth.passwordPh')} autoComplete="new-password" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        {serverError && <div className="u-error" role="alert">{serverError}</div>}

        <Button type="submit" className="mt-2 w-full" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting ? (
            <>
              <Loader2 className="size-4 animate-spin" />
              {t('auth.creating')}
            </>
          ) : t('auth.createAccount')}
        </Button>
      </form>
    </Form>
  )
}
