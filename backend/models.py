
from pydantic import BaseModel, Field
from typing import Optional, Literal
from datetime import datetime


class FieldValue(BaseModel):
    value: Optional[str] = None
    confidence: float = Field(default=0.0, ge=0.0, le=1.0)


class NameplateDetails(BaseModel):
    make: FieldValue
    model: FieldValue
    serial_number: FieldValue
    manufacture_date: FieldValue


class DamageDetails(BaseModel):
    room: FieldValue
    surface: FieldValue
    damage_type: FieldValue
    rough_size: FieldValue


class InspectionRecord(BaseModel):
    job_id: str
    capture_type: Literal["nameplate", "damage", "note"]
    captured_at: datetime
    frame_id: Optional[str] = None
    nameplate: Optional[NameplateDetails] = None
    damage: Optional[DamageDetails] = None
    note: Optional[FieldValue] = None
