#ifndef ASTRA_ECO3_H
#define ASTRA_ECO3_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct AstraEco AstraEco;

AstraEco *astra_eco_criar(int taxa, int canais);
void astra_eco_destruir(AstraEco *eco);

int astra_eco_ajustar(AstraEco *eco, int ruido, int ganho);

int astra_eco_referencia(AstraEco *eco, const int16_t *quadro, int amostras);
int astra_eco_capturar(AstraEco *eco, int16_t *quadro, int amostras, int atrasoMs);

#ifdef __cplusplus
}
#endif

#endif
