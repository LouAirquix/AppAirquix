package com.example.airquix01

import android.os.Parcel
import android.os.Parcelable

data class ActivityData(val type: Int, val confidence: Int) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(type)
        parcel.writeInt(confidence)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ActivityData> {
        override fun createFromParcel(parcel: Parcel): ActivityData {
            return ActivityData(parcel)
        }

        override fun newArray(size: Int): Array<ActivityData?> {
            return arrayOfNulls(size)
        }
    }
}
