package com.example.startline.nas

/**
 * Fiksni NAS API (reverse proxy / javni URL i API_KEY iz docker/.env).
 * Nisu vidljivi u Postavkama — mijenjaju se ovdje u kodu pri potrebi.
 */
object NasDefaults {
    const val BASE_URL: String = "https://bela3.synology.me"
    const val API_KEY: String = "jkbmnvbDRdfhgfd456FGHJ"
}
