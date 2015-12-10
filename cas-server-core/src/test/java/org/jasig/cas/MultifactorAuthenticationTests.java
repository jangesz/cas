package org.jasig.cas;

import org.jasig.cas.authentication.AuthenticationContext;
import org.jasig.cas.authentication.AuthenticationTransactionManager;
import org.jasig.cas.authentication.OneTimePasswordCredential;
import org.jasig.cas.authentication.SuccessfulHandlerMetaDataPopulator;
import org.jasig.cas.authentication.TestUtils;
import org.jasig.cas.authentication.UsernamePasswordCredential;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.jasig.cas.ticket.UnsatisfiedAuthenticationPolicyException;
import org.jasig.cas.validation.Assertion;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * High-level MFA functionality tests that leverage registered service metadata
 * ala {@link org.jasig.cas.authentication.RequiredHandlerAuthenticationPolicyFactory} to drive
 * authentication policy.
 *
 * @author Marvin S. Addison
 * @since 4.0.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/mfa-test-context.xml"})
public class MultifactorAuthenticationTests {

    @Autowired
    @Qualifier("centralAuthenticationService")
    private CentralAuthenticationService cas;

    @Autowired
    private AuthenticationTransactionManager authenticationTransactionManager;

    @Before
    public void setup() {
        this.authenticationTransactionManager.clear();
    }

    @Test
    public void verifyAllowsAccessToNormalSecurityServiceWithPassword() throws Exception {
        authenticationTransactionManager.processAuthenticationAttempt(newUserPassCredentials("alice", "alice"));
        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        final ServiceTicket st = cas.grantServiceTicket(tgt.getId(), newService("https://example.com/normal/"), ctx);
        assertNotNull(st);
    }

    @Test
    public void verifyAllowsAccessToNormalSecurityServiceWithOTP() throws Exception {
        authenticationTransactionManager.processAuthenticationAttempt(new OneTimePasswordCredential("alice", "31415"));
        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        final ServiceTicket st = cas.grantServiceTicket(tgt.getId(), newService("https://example.com/normal/"), ctx);
        assertNotNull(st);
    }

    @Test(expected = UnsatisfiedAuthenticationPolicyException.class)
    public void verifyDeniesAccessToHighSecurityServiceWithPassword() throws Exception {
        authenticationTransactionManager.processAuthenticationAttempt(newUserPassCredentials("alice", "alice"));
        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        cas.grantServiceTicket(tgt.getId(), newService("https://example.com/high/"), ctx);
    }

    @Test(expected = UnsatisfiedAuthenticationPolicyException.class)
    public void verifyDeniesAccessToHighSecurityServiceWithOTP() throws Exception {
        authenticationTransactionManager.processAuthenticationAttempt(new OneTimePasswordCredential("alice", "31415"));
        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        final ServiceTicket st = cas.grantServiceTicket(tgt.getId(), newService("https://example.com/high/"), ctx);
        assertNotNull(st);
    }

    @Test
    public void verifyAllowsAccessToHighSecurityServiceWithPasswordAndOTP() throws Exception {
        authenticationTransactionManager.processAuthenticationAttempt(newUserPassCredentials("alice", "alice"),
                new OneTimePasswordCredential("alice", "31415"));

        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        final ServiceTicket st = cas.grantServiceTicket(tgt.getId(), newService("https://example.com/high/"), ctx);
        assertNotNull(st);
    }

    @Test
    public void verifyAllowsAccessToHighSecurityServiceWithPasswordAndOTPViaRenew() throws Exception {
        // Note the original credential used to start SSO session does not satisfy security policy
        authenticationTransactionManager.processAuthenticationAttempt(newUserPassCredentials("alice", "alice"));
        final AuthenticationContext ctx = authenticationTransactionManager.build();

        final TicketGrantingTicket tgt = cas.createTicketGrantingTicket(ctx);
        assertNotNull(tgt);
        final Service service = newService("https://example.com/high/");

        authenticationTransactionManager.processAuthenticationAttempt(newUserPassCredentials("alice", "alice"),
                new OneTimePasswordCredential("alice", "31415"));

        final AuthenticationContext ctx2 = authenticationTransactionManager.build();

        final ServiceTicket st = cas.grantServiceTicket(
                tgt.getId(),
                service,
                ctx2);

        assertNotNull(st);
        // Confirm the authentication in the assertion is the one that satisfies security policy
        final Assertion assertion = cas.validateServiceTicket(st.getId(), service);
        assertEquals(2, assertion.getPrimaryAuthentication().getSuccesses().size());
        assertTrue(assertion.getPrimaryAuthentication().getSuccesses().containsKey("passwordHandler"));
        assertTrue(assertion.getPrimaryAuthentication().getSuccesses().containsKey("oneTimePasswordHandler"));
        assertTrue(assertion.getPrimaryAuthentication().getAttributes().containsKey(
                SuccessfulHandlerMetaDataPopulator.SUCCESSFUL_AUTHENTICATION_HANDLERS));
    }

    private static UsernamePasswordCredential newUserPassCredentials(final String user, final String pass) {
        final UsernamePasswordCredential userpass = new UsernamePasswordCredential();
        userpass.setUsername(user);
        userpass.setPassword(pass);
        return userpass;
    }

    private static Service newService(final String id) {
        return TestUtils.getService(id);
    }
}
