package org.openexposuretrace.oextrace.api

import org.openexposuretrace.oextrace.data.ContactRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


interface ContactsApiEndpoint {

    @POST("makeContact")
    fun sendContactRequest(@Body contactRequest: ContactRequest): Call<Void>

}