package com.cato.duneadmin.update

import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateServiceTest {
    @Test
    fun `config points at canonical public repository`() {
        assertEquals(
            "https://api.github.com/repos/ucwxcato/ArkSA-catos-admin-panel/releases/latest",
            UpdateConfig().latestReleaseUrl,
        )
    }
}
