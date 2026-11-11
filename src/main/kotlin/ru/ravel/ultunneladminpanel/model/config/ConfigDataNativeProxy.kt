package ru.ravel.ultunneladminpanel.model.config

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import ru.ravel.ultunneladminpanel.service.SerializationContext

// Store the complete client configuration so credentials survive refreshes and restarts.
@Entity
class ConfigDataNativeProxy(
    @Column(columnDefinition = "text") @JsonIgnore
    var outboundJson: String = "{}",
    @Column(columnDefinition = "text") @JsonIgnore
    var auxiliaryOutboundsJson: String = "[]",
    @JsonIgnore var endpoint: Boolean? = false,
) : ConfigData() {
    @get:JsonIgnore
    override var type: String?
        get() = super.type
        set(value) { super.type = value }

    @get:JsonIgnore
    override var server: String?
        get() = super.server
        set(value) { super.server = value }

    @JsonAnyGetter
    fun outboundFields(): Map<String, Any?> {
        val fields = ObjectMapper().readValue(outboundJson,
            object : TypeReference<MutableMap<String, Any?>>() {})
        if (!SerializationContext.isConfigCall()) {
            fields["type"] = type
            fields["server"] = server
            fields["proxy"] = proxy?.let { mapOf("id" to it.id, "type" to it.type) }
        }
        return fields
    }

    override fun fillFields(): ConfigDataNativeProxy = this
}
