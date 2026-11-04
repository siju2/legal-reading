package com.example.project.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DataCipherTest {
    @Test void randomNoncesAndUnicodeRoundTrip(){String value="비공개 상담 내용 🔐";String a=DataCipher.encryptText(value),b=DataCipher.encryptText(value);assertThat(a).isNotEqualTo(b).doesNotContain("비공개");assertThat(DataCipher.decryptText(a)).isEqualTo(value);}
    @Test void tamperFailsAuthentication(){byte[] encrypted=DataCipher.encrypt(new byte[]{1,2,3});encrypted[encrypted.length-1]^=1;assertThatThrownBy(()->DataCipher.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);}
    @Test void plainDatabaseValuesAreRejected(){assertThatThrownBy(()->DataCipher.decryptText("plaintext")).isInstanceOf(IllegalStateException.class);}
    @Test void nullAndEmptyValuesRemainDistinct(){assertThat(DataCipher.decryptText(DataCipher.encryptText(null))).isNull();assertThat(DataCipher.decryptText(DataCipher.encryptText(""))).isEmpty();}
    @Test void fileEnvelopeAuthenticatesAndRecoversBinary(){byte[] input={0,1,2,-1,20};byte[] encrypted=DataCipher.encryptFile(input);assertThat(encrypted).isNotEqualTo(input);assertThat(DataCipher.decryptFile(encrypted)).isEqualTo(input);assertThatThrownBy(()->DataCipher.decryptFile(input)).isInstanceOf(IllegalStateException.class);}
}
