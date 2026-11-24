package com.arnix.cinema.data

import android.content.Context

/**
 * HDRezka metadata/catalog access only.
 * Video stream extraction and playback remain in HdRezkaService.
 */
class HdRezkaCatalogService private constructor(context: Context) {
    private val delegate = HdRezkaService.create(context)

    suspend fun catalog(category: String, page: Int = 1): RezkaPage =
        delegate.catalog(category, page)

    suspend fun search(query: String): RezkaPage =
        delegate.search(query)

    suspend fun details(url: String): RezkaDetails =
        delegate.details(url)

    companion object {
        fun create(context: Context): HdRezkaCatalogService =
            HdRezkaCatalogService(context.applicationContext)
    }
}
