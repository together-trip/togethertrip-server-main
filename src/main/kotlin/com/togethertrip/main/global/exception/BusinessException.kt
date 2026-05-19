package com.togethertrip.main.global.exception

class BusinessException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)