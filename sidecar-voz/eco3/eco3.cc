#include "eco3.h"

#include "api/audio/audio_processing.h"

struct AstraEco {
  webrtc::scoped_refptr<webrtc::AudioProcessing> apm;
  webrtc::StreamConfig formato;
};

extern "C" {

AstraEco *astra_eco_criar(int taxa, int canais) {
  auto apm = webrtc::AudioProcessingBuilder().Create();
  if (apm == nullptr) return nullptr;

  webrtc::AudioProcessing::Config config;
  config.echo_canceller.enabled = true;
  config.echo_canceller.mobile_mode = false;
  config.noise_suppression.enabled = true;
  config.gain_controller2.enabled = true;
  apm->ApplyConfig(config);

  AstraEco *eco = new AstraEco();
  eco->apm = apm;
  eco->formato = webrtc::StreamConfig(taxa, canais);
  return eco;
}

void astra_eco_destruir(AstraEco *eco) { delete eco; }

int astra_eco_referencia(AstraEco *eco, const int16_t *quadro, int amostras) {
  if (eco == nullptr) return -1;
  return eco->apm->ProcessReverseStream(quadro, eco->formato, eco->formato,
                                        const_cast<int16_t *>(quadro));
}

int astra_eco_capturar(AstraEco *eco, int16_t *quadro, int amostras, int atrasoMs) {
  if (eco == nullptr) return -1;
  eco->apm->set_stream_delay_ms(atrasoMs);
  return eco->apm->ProcessStream(quadro, eco->formato, eco->formato, quadro);
}

}
