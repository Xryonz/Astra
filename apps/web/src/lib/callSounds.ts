/**
 * Sons de chamada — sintetizados via WebAudio (sem assets, leve).
 * Mesmo approach do ring do IncomingCallModal: dois tons curtos.
 *
 * playCallJoin: "blip" ascendente ao conectar numa call (norma Discord).
 * playCallLeave: descendente ao sair.
 */

function blip(freqs: number[], gap = 0.09, dur = 0.12, vol = 0.06) {
  try {
    const ctx = new (window.AudioContext || (window as any).webkitAudioContext)()
    freqs.forEach((f, i) => {
      const osc  = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = f
      const t0 = ctx.currentTime + i * gap
      gain.gain.setValueAtTime(0, t0)
      gain.gain.linearRampToValueAtTime(vol, t0 + 0.012)
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t0)
      osc.stop(t0 + dur)
    })
    // Fecha o contexto após o último tom pra não vazar AudioContexts
    setTimeout(() => { void ctx.close().catch(() => {}) }, (freqs.length * gap + dur) * 1000 + 60)
  } catch { /* WebAudio indisponível — silencioso */ }
}

/** Entrou na call: dó→sol ascendente. Volume alto (entrar é o evento
 *  que o user quer ouvir claramente, mesmo com o app em outra coisa). */
export function playCallJoin() { blip([523.25, 783.99], 0.1, 0.16, 0.22) }

/** Saiu da call: sol→dó descendente, mais discreto. */
export function playCallLeave() { blip([783.99, 523.25], 0.09, 0.12, 0.1) }
