package com.kuromelabs.kurome

object Packets {
    const val RESULT_ACTION_SUCCESS: Byte = 0
    const val ACTION_GET_ENUMERATE_DIRECTORY: Byte = 1
    const val ACTION_GET_SPACE_INFO: Byte = 2
    const val ACTION_GET_FILE_TYPE: Byte = 3
    const val ACTION_WRITE_DIRECTORY: Byte = 4
    const val RESULT_FILE_IS_DIRECTORY: Byte = 5
    const val RESULT_FILE_IS_FILE: Byte = 6
    const val RESULT_FILE_NOT_FOUND: Byte = 7
    const val ACTION_DELETE: Byte = 8
    const val RESULT_ACTION_FAIL: Byte = 9
    const val ACTION_SEND_TO_SERVER: Byte = 10
    const val ACTION_GET_FILE_INFO: Byte = 11
}