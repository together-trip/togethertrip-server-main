package com.togethertrip.main.global.security.local

import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserRole
import com.togethertrip.main.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalTestAuthenticationServiceTest {

    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        userRepository = mock(UserRepository::class.java) { invocation ->
            if (invocation.method.name == "save") {
                (invocation.arguments[0] as User).apply { id = 100L }
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }
    }

    @Test
    fun `비활성화되거나 prefix가 다른 token은 인증하지 않는다`() {
        assertNull(service(enabled = false).authenticate("local-test:user"))
        assertNull(service().authenticate("Bearer local-test:user"))
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `빈 식별자는 인증하지 않는다`() {
        assertNull(service().authenticate("local-test:"))
        verifyNoInteractions(userRepository)
    }

    @Test
    fun `일반 token은 전화번호 없이 사용자를 만든다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 jaewan"))
            .thenReturn(null)

        val authUser = service().authenticate("local-test:jaewan")

        assertEquals(100L, authUser?.userId)
        assertEquals(UserRole.USER, authUser?.role)
        assertEquals("로컬 jaewan", savedUser().nickname)
    }

    @Test
    fun `기존 verified token 별칭을 계속 지원한다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 verified jaewan"))
            .thenReturn(null)

        service().authenticate("local-test:verified:jaewan")

        assertEquals("로컬 verified jaewan", savedUser().nickname)
    }

    @Test
    fun `admin token은 관리자 사용자를 만든다`() {
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 admin")).thenReturn(null)

        val authUser = service().authenticate("local-test:admin")

        assertEquals(UserRole.ADMIN, authUser?.role)
        assertEquals(UserRole.ADMIN, savedUser().role)
    }

    @Test
    fun `기존 admin 사용자의 권한을 보정한다`() {
        val existing = User(nickname = "로컬 admin").apply { id = 77L }
        `when`(userRepository.findByNicknameAndDeletedAtIsNull("로컬 admin"))
            .thenReturn(existing)

        val authUser = service().authenticate("local-test:admin")

        assertEquals(UserRole.ADMIN, authUser?.role)
        assertEquals(UserRole.ADMIN, existing.role)
    }

    private fun service(enabled: Boolean = true): LocalTestAuthenticationService {
        return LocalTestAuthenticationService(
            userRepository = userRepository,
            enabled = enabled,
        )
    }

    private fun savedUser(): User {
        val invocation = org.mockito.Mockito.mockingDetails(userRepository).invocations
            .last { it.method.name == "save" }
        return invocation.arguments[0] as User
    }
}
