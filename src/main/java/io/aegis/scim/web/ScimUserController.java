package io.aegis.scim.web;

import io.aegis.scim.domain.ScimUser;
import io.aegis.scim.service.ScimExceptions.ScimBadRequestException;
import io.aegis.scim.service.ScimUserService;
import io.aegis.scim.service.ScimUserService.UserInput;
import io.aegis.scim.web.ScimDtos.Email;
import io.aegis.scim.web.ScimDtos.ListResponse;
import io.aegis.scim.web.ScimDtos.Name;
import io.aegis.scim.web.ScimDtos.PatchOp;
import io.aegis.scim.web.ScimDtos.ScimUserResource;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCIM 2.0 Users provider (RFC 7644). Authenticated by the per-connector bearer token via
 * {@link ScimConnectorAuthFilter}, which resolves the tenant into a request attribute — never from the
 * JWT chain and never from the body. Accepts {@code application/scim+json} or {@code application/json}.
 */
@RestController
@RequestMapping(path = "/scim/v2/Users",
        produces = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
public class ScimUserController {

    /** Matches the standard IdP lookup filter: {@code userName eq "value"}. */
    private static final Pattern USERNAME_EQ =
            Pattern.compile("^\\s*userName\\s+eq\\s+\"([^\"]*)\"\\s*$", Pattern.CASE_INSENSITIVE);

    private final ScimUserService users;

    public ScimUserController(ScimUserService users) {
        this.users = users;
    }

    @PostMapping(consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ScimUserResource> create(@RequestBody ScimUserResource body,
                                                   HttpServletRequest request) {
        String tenantId = tenantOf(request);
        ScimUser created = users.create(tenantId, toInput(body));
        return ResponseEntity
                .created(URI.create("/scim/v2/Users/" + created.getId()))
                .body(ScimUserResource.from(created));
    }

    @GetMapping("/{id}")
    public ScimUserResource get(@PathVariable UUID id, HttpServletRequest request) {
        return ScimUserResource.from(users.get(tenantOf(request), id));
    }

    @GetMapping
    public ListResponse list(@RequestParam(name = "filter", required = false) String filter,
                             @RequestParam(name = "startIndex", defaultValue = "1") int startIndex,
                             @RequestParam(name = "count", defaultValue = "100") int count,
                             HttpServletRequest request) {
        String tenantId = tenantOf(request);
        List<ScimUser> matched;
        if (filter != null && !filter.isBlank()) {
            Matcher m = USERNAME_EQ.matcher(filter);
            if (!m.matches()) {
                throw new ScimBadRequestException("unsupported filter (only 'userName eq \"...\"' is supported)");
            }
            matched = users.findByUserName(tenantId, m.group(1));
        } else {
            matched = users.list(tenantId);
        }
        int total = matched.size();
        int from = Math.max(0, startIndex - 1);
        int to = count <= 0 ? from : Math.min(total, from + count);
        List<ScimUserResource> page = (from >= total)
                ? List.of()
                : matched.subList(from, to).stream().map(ScimUserResource::from).toList();
        return ListResponse.of(page, startIndex, page.size(), total);
    }

    @PutMapping(path = "/{id}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ScimUserResource replace(@PathVariable UUID id, @RequestBody ScimUserResource body,
                                    HttpServletRequest request) {
        return ScimUserResource.from(users.replace(tenantOf(request), id, toInput(body)));
    }

    @PatchMapping(path = "/{id}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ScimUserResource patch(@PathVariable UUID id, @RequestBody PatchOp body,
                                  HttpServletRequest request) {
        String tenantId = tenantOf(request);
        Boolean active = extractActive(body);
        if (active == null) {
            throw new ScimBadRequestException("only 'replace' of 'active' is supported");
        }
        return ScimUserResource.from(users.setActive(tenantId, id, active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        users.delete(tenantOf(request), id);
        return ResponseEntity.noContent().build();
    }

    // --- mapping helpers ---

    private static UserInput toInput(ScimUserResource body) {
        if (body == null) {
            throw new ScimBadRequestException("request body is required");
        }
        String givenName = body.name() == null ? null : body.name().givenName();
        String familyName = body.name() == null ? null : body.name().familyName();
        String email = primaryEmail(body.emails());
        boolean active = body.active() == null || body.active();
        return new UserInput(body.externalId(), body.userName(), email, givenName, familyName, active);
    }

    private static String primaryEmail(List<Email> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.primary()) && e.value() != null)
                .map(Email::value)
                .findFirst()
                .orElseGet(() -> emails.stream()
                        .map(Email::value)
                        .filter(v -> v != null)
                        .findFirst()
                        .orElse(null));
    }

    /** Extracts a {@code replace} of {@code active} from a SCIM PatchOp (path-scoped or top-level value). */
    private static Boolean extractActive(PatchOp body) {
        if (body == null || body.operations() == null) {
            return null;
        }
        for (PatchOp.Operation op : body.operations()) {
            if (op.op() == null || !op.op().equalsIgnoreCase("replace")) {
                continue;
            }
            // Form 1: { "op":"replace", "path":"active", "value": true|false|"true" }
            if (op.path() != null && op.path().equalsIgnoreCase("active")) {
                return toBool(op.value());
            }
            // Form 2: { "op":"replace", "value": { "active": true } }
            if (op.path() == null && op.value() instanceof java.util.Map<?, ?> map && map.containsKey("active")) {
                return toBool(map.get("active"));
            }
        }
        return null;
    }

    private static Boolean toBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    private static String tenantOf(HttpServletRequest request) {
        Object tenant = request.getAttribute(ScimConnectorAuthFilter.TENANT_ATTRIBUTE);
        if (tenant == null) {
            // Should be unreachable — the connector filter authenticates before the controller runs.
            throw new ScimBadRequestException("connector tenant not resolved");
        }
        return tenant.toString();
    }
}
