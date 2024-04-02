package software.amazon.codeartifact.packagegroup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractPackageGroupArnTest extends AbstractTestBase{

    @Test
    public void decode_encoded_asterisk() {
        PackageGroupArn result = PackageGroupArn.fromArn(PGC_ARN_WITH_DOMAIN_OWNER);

        assertEquals("arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/npm/%2a", result.arn());
        assertEquals("/npm/*", result.packageGroupName());
    }

    @Test
    public void decode_encoded_dollar() {
        String arnWithDollar = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/nuget//alpha%24";
        PackageGroupArn result = PackageGroupArn.fromArn(arnWithDollar);

        assertEquals("arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/nuget//alpha%24", result.arn());
        assertEquals("/nuget//alpha$", result.packageGroupName());
    }

    @Test
    public void decode_encoded_percents() {
        String arnWithPercents = "arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/nuget//alpha%25beta%25gamma~";
        PackageGroupArn result = PackageGroupArn.fromArn(arnWithPercents);

        assertEquals("arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/nuget//alpha%25beta%25gamma~", result.arn());
        assertEquals("/nuget//alpha%beta%gamma~", result.packageGroupName());
    }

    @Test
    public void incomplete_encoded_character() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> PackageGroupArn.fromArn("arn:aws:codeartifact:us-west-2:12345:package-group/test-domain-name/nuget//alpha%2")
        );

        String expectedExceptionMessage = "URLDecoder: Incomplete trailing escape (%) pattern";
        assertEquals(expectedExceptionMessage, e.getMessage());
    }
}
