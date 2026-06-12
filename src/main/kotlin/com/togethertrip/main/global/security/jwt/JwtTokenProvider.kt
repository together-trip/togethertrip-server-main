package com.togethertrip.main.global.security.jwt

import com.togethertrip.main.user.domain.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun createAccessToken(
        userId: Long,
        role: UserRole,
    ): String {
        return createToken(
            userId = userId,
            role = role,
            tokenType = TokenType.ACCESS,
            expirationSeconds = jwtProperties.accessTokenExpiration,
        )
    }

    fun createRefreshToken(
        userId: Long,
        role: UserRole,
    ): String {
        return createToken(
            userId = userId,
            role = role,
            tokenType = TokenType.REFRESH,
            expirationSeconds = jwtProperties.refreshTokenExpiration,
        )
    }

    fun validateToken(token: String): Boolean {
        return try {
            // token claim 구조 검증
            getClaims(token)
            true
        } catch (exception: ExpiredJwtException) {
            false
        } catch (exception: JwtException) {
            false
        } catch (exception: ClassCastException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    fun getClaims(token: String): JwtClaims {
        val claims = parseClaims(token)

        // JWT claim 변환
        val userId = claims.subject.toLong()
        val role = UserRole.valueOf(claims["role"] as String)
        val tokenType = TokenType.valueOf(claims["tokenType"] as String)

        // JWT claim 응답
        return JwtClaims(
            userId = userId,
            role = role,
            tokenType = tokenType,
        )
    }

    fun getExpirationSeconds(tokenType: TokenType): Long {
        return when (tokenType) {
            TokenType.ACCESS -> jwtProperties.accessTokenExpiration
            TokenType.REFRESH -> jwtProperties.refreshTokenExpiration
        }
    }

    private fun createToken(
        userId: Long,
        role: UserRole,
        tokenType: TokenType,
        expirationSeconds: Long,
    ): String {
        val now = Instant.now()
        val expiration = now.plusSeconds(expirationSeconds)

        // JWT 생성
        return Jwts.builder()
            .issuer(jwtProperties.issuer)
            .subject(userId.toString())
            .claim("role", role.name)
            .claim("tokenType", tokenType.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(secretKey)
            .compact()
    }

    private fun parseClaims(token: String): Claims {
        // JWT 파싱
        return Jwts.parser()
            .verifyWith(secretKey)
            .requireIssuer(jwtProperties.issuer)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}
