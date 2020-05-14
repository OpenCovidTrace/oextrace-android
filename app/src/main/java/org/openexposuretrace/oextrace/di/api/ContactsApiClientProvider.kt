package org.openexposuretrace.oextrace.di.api

import org.openexposuretrace.oextrace.BuildConfig.API_HOST
import org.openexposuretrace.oextrace.api.ContactsApiClient
import org.openexposuretrace.oextrace.api.ContactsApiEndpoint
import org.openexposuretrace.oextrace.di.IndependentProvider
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

internal object ContactsApiClientProvider : IndependentProvider<ContactsApiClient>() {

    private val httpClient by HttpClientProvider()

    const val CONTACTS_ENDPOINT = "https://contact.$API_HOST/"

    override fun initInstance(): ContactsApiClient = Retrofit.Builder()
        .baseUrl(CONTACTS_ENDPOINT)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ContactsApiEndpoint::class.java)
        .run { ContactsApiClient(this) }

}


