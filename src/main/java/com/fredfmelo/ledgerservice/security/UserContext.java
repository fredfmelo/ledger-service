package com.fredfmelo.ledgerservice.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fredfmelo.eventdrivencore.exception.BusinessException;
import com.fredfmelo.ledgerservice.ledger.domain.Role;

@Component
public class UserContext {

    private ServletRequestAttributes getAtrributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }

    public UUID getUserId() {
        try {
            return UUID.fromString(getAtrributes().getRequest().getHeader("X-User-Id"));
        } catch (Exception ex) {
            throw new BusinessException("Error getting user from token jwt", HttpStatus.FORBIDDEN.value());
        }
    }

    public String getEmail() {
        return getAtrributes().getRequest().getHeader("X-User-Email");
    }

    public Role getRole() {
        try {
            return Role.valueOf(getAtrributes().getRequest().getHeader("X-User-Role"));
        } catch (Exception ex) {
            throw new BusinessException("Error getting role from token jwt", HttpStatus.FORBIDDEN.value());
        }
    }

}
