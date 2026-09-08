package main

import (
	"fmt"
	"os"

	"github.com/jchv/go-webview2"
)

const paginaDeProva = `<!doctype html>
<html><body><script>
  window.chrome.webview.postMessage(JSON.stringify({
    ev: "pronto",
    v: navigator.userAgent
  }));
</script></body></html>`

func main() {
	navegador := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: false,
		WindowOptions: webview2.WindowOptions{
			Title:  "Astra voz",
			Width:  1,
			Height: 1,
		},
	})
	if navegador == nil {
		fmt.Fprintln(os.Stderr, "motor do Edge indisponivel")
		os.Exit(1)
	}
	defer navegador.Destroy()

	navegador.Bind("avisar", func(texto string) {
		fmt.Println(texto)
	})

	navegador.SetHtml(paginaDeProva + `<script>avisar(navigator.userAgent)</script>`)
	navegador.Run()
}
