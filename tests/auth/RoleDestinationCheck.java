import com.example.footballbooking.ui.auth.RoleDestination;

/** Run without Android/Gradle so role-boundary regression checks remain available. */
public class RoleDestinationCheck {
    public static void main(String[] args) {
        check("customer", RoleDestination.CUSTOMER);
        check("owner", RoleDestination.OWNER);
        check("admin", RoleDestination.ADMIN);
        check("owner_pending", RoleDestination.PENDING);
        for (String role : new String[] {null, "", "OWNER", " owner", "superadmin", "blocked"}) {
            check(role, RoleDestination.DENIED);
        }
        System.out.println("10 role routing cases passed");
    }

    private static void check(String role, RoleDestination expected) {
        if (RoleDestination.fromRole(role) != expected) {
            throw new AssertionError("Unexpected route for role: " + role);
        }
    }
}
