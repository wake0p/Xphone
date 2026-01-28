package com.safe.discipline.data.model

import java.util.UUID

data class AppGroup(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val packages: Set<String>,
        val color: Int = 0 // 使用 Int 存储颜色值 (ARGB)
)
