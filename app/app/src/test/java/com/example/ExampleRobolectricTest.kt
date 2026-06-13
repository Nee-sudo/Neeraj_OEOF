package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.UserEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("One Earth", appName)
  }

  @Test
  fun `test moshi parsing of UserEntity`() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(UserEntity::class.java)
    val jsonString = """
      {
        "_id": "ritik@gmail.com",
        "id": "me",
        "name": "ritik",
        "username": "@ritik",
        "email": "ritik@gmail.com",
        "dob": "2003-22-12",
        "territory": "Botswana",
        "flagEmoji": "🇧🇼",
        "gender": "Male",
        "currentRank": "Citizen",
        "knowledgeCredits": 50,
        "contributionCredits": 25,
        "reputationScore": 98,
        "personalityTraits": "Explorer,Creator,Teacher,Leader,Scientist",
        "bio": "Honorable citizen of the digital Empire. Committed to service and know…",
        "followers": 120,
        "following": 95,
        "onboardingCompleted": true,
        "citizenOathAccepted": true,
        "isCandidate": false,
        "campaignManifesto": "",
        "campaignVision": "",
        "votesCount": 0,
        "hasVoted": false,
        "profilePhoto": ""
      }
    """.trimIndent()
    val user = adapter.fromJson(jsonString)
    assertNotNull(user)
    assertEquals("ritik", user?.name)
  }

  @Test
  fun `test moshi parsing of UserDto with numeric id`() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(com.example.data.UserDto::class.java)
    val jsonString = """
      {
        "_id": "6a2b0425b73cae2a9d9dad78",
        "id": 2,
        "name": "Aryan Sharma",
        "username": "@aryan.s",
        "email": "aryan.sharma@oeof.org"
      }
    """.trimIndent()
    try {
      val user = adapter.fromJson(jsonString)
      assertNotNull(user)
      println("Parsed ID successfully: ${user?.id}")
    } catch (e: Exception) {
      println("FAILED TO PARSE WITH NUMERIC ID: ${e.message}")
      throw e
    }
  }
}

