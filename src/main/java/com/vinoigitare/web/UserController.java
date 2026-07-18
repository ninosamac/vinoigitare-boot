package com.vinoigitare.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The "User" nav tab (2026-07-18, Nino's request) -- a real page, linked
 * exactly like {@link AdminController}'s Admin tab rather than a
 * dropdown, currently just housing a link to {@code /songbook}. Mirrors
 * {@link AboutController}'s shape (the closest existing precedent for a
 * simple static-content page): no database, no per-request state, all
 * content lives directly in {@code user.html} via message-bundle keys.
 *
 * <p>A separate, bigger nav-tab idea covering the same underlying need
 * (view/buy the songbook, plus moving the feedback form here) was scoped
 * then explicitly shelved the same day, pending more thought on overall
 * menu structure -- this is a narrower, concrete piece of that same
 * need, not a reversal of shelving the bigger idea. Room for genuinely
 * account-ish content later, if this app ever grows any (it doesn't have
 * visitor accounts today -- see {@code admin-auth-plan.md}).
 */
@Controller
public class UserController {

    @GetMapping("/user")
    public String user() {
        return "user";
    }
}
