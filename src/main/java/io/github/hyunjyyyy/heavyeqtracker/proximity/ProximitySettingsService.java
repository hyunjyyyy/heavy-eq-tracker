package io.github.hyunjyyyy.heavyeqtracker.proximity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProximitySettingsService {

    private final SiteProximitySettingsRepository repository;
    private final ProximitySettingsResolver resolver;
    private final Validator validator;
    private final ApplicationEventPublisher events;

    public ProximitySettingsService(SiteProximitySettingsRepository repository, ProximitySettingsResolver resolver,
                                    Validator validator, ApplicationEventPublisher events) {
        this.repository = repository;
        this.resolver = resolver;
        this.validator = validator;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ProximitySettingsView get(long siteId) {
        return repository.findById(siteId)
                .map(s -> view(s, resolver.merge(s)))
                .orElseGet(() -> new ProximitySettingsView(
                        new ProximitySettingsRequest(null, null, null, null, null),
                        ProximitySettingsView.Effective.of(resolver.defaults())));
    }

    @Transactional
    public ProximitySettingsView update(long siteId, ProximitySettingsRequest r) {
        SiteProximitySettings s = repository.findById(siteId).orElseGet(() -> new SiteProximitySettings(siteId));
        s.update(r.thresholdM(), r.maxErrorM(), r.staleAfterSeconds(), r.defaultPhoneErrorM(), r.defaultRtkErrorM());

        /* 현장 값만 맞아도 기본값과 합치면 틀릴 수 있다. 합친 결과를 yml과 같은 제약으로 다시 본다(#32 결정 2) */
        ProximityProperties merged = resolver.merge(s);
        Set<ConstraintViolation<ProximityProperties>> violations = validator.validate(merged);
        if (!violations.isEmpty()) {
            throw new InvalidProximitySettingsException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", ")));
        }

        repository.save(s);
        events.publishEvent(new ProximitySettingsChanged(siteId));
        return view(s, merged);
    }

    private static ProximitySettingsView view(SiteProximitySettings s, ProximityProperties merged) {
        return new ProximitySettingsView(
                new ProximitySettingsRequest(s.getThresholdM(), s.getMaxErrorM(), s.getStaleAfterSeconds(),
                        s.getDefaultPhoneErrorM(), s.getDefaultRtkErrorM()),
                ProximitySettingsView.Effective.of(merged));
    }
}
