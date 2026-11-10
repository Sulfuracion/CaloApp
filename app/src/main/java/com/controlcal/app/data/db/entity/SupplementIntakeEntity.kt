package com.controlcal.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.controlcal.core.supplement.IntakeStatus
import com.controlcal.core.supplement.SupplementIntake
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "supplement_intakes",
    indices = [
        Index(value = ["supplement_id", "scheduled_date", "scheduled_time"], unique = true),
        Index(value = ["scheduled_date"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SupplementEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplement_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SupplementIntakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "supplement_id")
    val supplementId: Long,
    @ColumnInfo(name = "scheduled_date")
    val scheduledDate: String,
    @ColumnInfo(name = "scheduled_time")
    val scheduledTime: String,
    val status: String,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
) {
    fun toDomain(): SupplementIntake = SupplementIntake(
        id = id,
        supplementId = supplementId,
        scheduledDate = LocalDate.parse(scheduledDate),
        scheduledTime = LocalTime.parse(scheduledTime),
        status = IntakeStatus.valueOf(status),
        recordedAt = recordedAt,
    )

    companion object {
        fun fromDomain(domain: SupplementIntake): SupplementIntakeEntity = SupplementIntakeEntity(
            id = domain.id,
            supplementId = domain.supplementId,
            scheduledDate = domain.scheduledDate.toString(),
            scheduledTime = domain.scheduledTime.toString(),
            status = domain.status.name,
            recordedAt = domain.recordedAt,
        )
    }
}
