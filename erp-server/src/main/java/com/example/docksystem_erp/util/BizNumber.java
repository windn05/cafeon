package com.example.docksystem_erp.util;

// 사업자등록번호는 DB에 숫자 10자리만 저장한다. ("123-45-67890" → "1234567890")
// 화면에서 하이픈을 붙여 보여 주는 건 프론트엔드가 맡는다.
public final class BizNumber {
    private BizNumber() {}

    public static String digitsOnly(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }
}
