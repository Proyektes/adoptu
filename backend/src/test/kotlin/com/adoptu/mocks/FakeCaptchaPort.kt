package com.adoptu.mocks

import com.adoptu.ports.CaptchaPort

class FakeCaptchaPort(private var valid: Boolean = true) : CaptchaPort {
    fun setValid(value: Boolean) {
        valid = value
    }

    override suspend fun verify(token: String, remoteIp: String?): Boolean = valid
}
