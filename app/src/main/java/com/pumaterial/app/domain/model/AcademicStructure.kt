package com.pumaterial.app.domain.model

data class Department(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val description: String = "",
    val order: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class AcademicYear(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val order: Int = 0,
    val isActive: Boolean = true
)

data class Section(
    val id: String = "",
    val name: String = "",
    val order: Int = 0,
    val isActive: Boolean = true,
    val isDefault: Boolean = true
)
