//go:build aec3

#include "eco3.h"

#include <vector>

#include "api/audio/audio_processing.h"

struct AstraEco {
  webrtc::scoped_refptr<webrtc::AudioProcessing> apm;
  webrtc::StreamConfig formato;
  std::vector<int16_t> descarte;
};

extern "C" {

static void aplicar(AstraEco *eco, bool ruido, bool ganho) {
  webrtc::AudioProcessing::Config config;
  config.echo_canceller.enabled = true;
  config.echo_canceller.mobile_mode = false;
  config.noise_suppression.enabled = ruido;
  config.gain_controller2.enabled = ganho;
  eco->apm->ApplyConfig(config);
}

AstraEco *astra_eco_criar(int taxa, int canais) {
  auto apm = webrtc::AudioProcessingBuilder().Create();
  if (apm == nullptr) return nullptr;

  AstraEco *eco = new AstraEco();
  eco->apm = apm;
  eco->formato = webrtc::StreamConfig(taxa, canais);
  aplicar(eco, true, true);
  return eco;
}

void astra_eco_destruir(AstraEco *eco) { delete eco; }

int astra_eco_ajustar(AstraEco *eco, int ruido, int ganho) {
  if (eco == nullptr) return -1;
  aplicar(eco, ruido != 0, ganho != 0);
  return 0;
}

int astra_eco_referencia(AstraEco *eco, const int16_t *quadro, int amostras) {
  if (eco == nullptr) return -1;
  if (static_cast<int>(eco->descarte.size()) != amostras) {
    eco->descarte.assign(amostras, 0);
  }
  return eco->apm->ProcessReverseStream(quadro, eco->formato, eco->formato,
                                        eco->descarte.data());
}

int astra_eco_capturar(AstraEco *eco, int16_t *quadro, int amostras, int atrasoMs) {
  if (eco == nullptr) return -1;
  eco->apm->set_stream_delay_ms(atrasoMs);
  return eco->apm->ProcessStream(quadro, eco->formato, eco->formato, quadro);
}

}
