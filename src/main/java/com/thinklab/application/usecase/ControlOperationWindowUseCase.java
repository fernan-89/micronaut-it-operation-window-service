package com.thinklab.application.usecase;

import com.thinklab.domain.exception.WindowNotFoundException;
import com.thinklab.domain.model.OperationWindow;
import com.thinklab.domain.model.OperationWindow.WindowStatus;
import com.thinklab.domain.repository.OperationWindowRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case governing the Operation Window lifecycle (BIAN Behavior Qualifier: {@code control}).
 *
 * <p><b>State Machine Enforcement:</b> loads the aggregate first, delegates the transition to the
 * domain model (which throws {@link com.thinklab.domain.exception.InvalidWindowStatusException} on an
 * illegal move) and only then issues the granular persistence update — never a blind partial write.
 */
@Singleton
public class ControlOperationWindowUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlOperationWindowUseCase.class);

    private final OperationWindowRepository repository;

    public ControlOperationWindowUseCase(OperationWindowRepository repository) {
        this.repository = repository;
    }

    public Mono<Void> execute(UUID id, Action action) {
        log.info("[USE CASE] Controlling operation window lifecycle: {} for ID: {}", action, id);

        return repository.findById(id)
                .switchIfEmpty(Mono.error(new WindowNotFoundException(id)))
                .flatMap(window -> {
                    action.apply(window);
                    return repository.updateStatus(id, action.targetStatus());
                });
    }

    public enum Action {
        START(WindowStatus.IN_PROGRESS) {
            @Override void apply(OperationWindow window) { window.start(); }
        },
        COMPLETE(WindowStatus.COMPLETED) {
            @Override void apply(OperationWindow window) { window.complete(); }
        },
        CANCEL(WindowStatus.CANCELLED) {
            @Override void apply(OperationWindow window) { window.cancel(); }
        };

        private final WindowStatus targetStatus;

        Action(WindowStatus targetStatus) {
            this.targetStatus = targetStatus;
        }

        public WindowStatus targetStatus() {
            return targetStatus;
        }

        abstract void apply(OperationWindow window);
    }
}
