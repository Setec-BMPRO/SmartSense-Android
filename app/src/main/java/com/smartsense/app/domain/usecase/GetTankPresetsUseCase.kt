package com.smartsense.app.domain.usecase

import com.smartsense.app.domain.model.TankPreset
import javax.inject.Inject

class GetTankPresetsUseCase @Inject constructor() {
    operator fun invoke(): List<TankPreset> = TankPreset.defaults
}
