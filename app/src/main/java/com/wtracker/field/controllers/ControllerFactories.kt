package com.wtracker.field.controllers

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wtracker.field.configs.AppContainer

/** Fábricas de ViewModels: único lugar donde controladores y contenedor DI se conectan. */
object ControllerFactories {

    fun appStart(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { AppStartViewModel(container.sessionStore, container.routePlanService) }
    }

    fun login(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { LoginViewModel(container.authService) }
    }

    fun routeWelcome(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { RouteWelcomeViewModel(container.authService, container.sessionStore, container.shiftService, container.appPreferencesStore) }
    }

    fun clientPicker(container: AppContainer, mode: ClientPickerMode): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ClientPickerViewModel(
                mode = mode,
                clientService = container.clientService,
                routePlanService = container.routePlanService,
                timeProvider = container.timeProvider,
                sessionStore = container.sessionStore,
            )
        }
    }

    fun routeSheet(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            RouteSheetViewModel(
                clientService = container.clientService,
                routePlanService = container.routePlanService,
                shiftService = container.shiftService,
                sessionExitService = container.sessionExitService,
                locationProvider = container.locationProvider,
                visitService = container.visitService,
                connectivityObserver = container.connectivityObserver,
                gpsObserver = container.gpsObserver,
                sessionStore = container.sessionStore,
                timeProvider = container.timeProvider,
                appPreferencesStore = container.appPreferencesStore,
            )
        }
    }

    fun visitCapture(container: AppContainer, clientId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            VisitCaptureViewModel(
                clientId = clientId,
                visitService = container.visitService,
                locationProvider = container.locationProvider,
                evidenceDraftStore = container.evidenceDraftStore,
                clientService = container.clientService,
                appPreferencesStore = container.appPreferencesStore,
                sessionStore = container.sessionStore,
                orderConfirmationStore = container.orderConfirmationStore,
            )
        }
    }

    fun orderCreation(container: AppContainer, clientId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            OrderCreationViewModel(
                clientId = clientId,
                clientService = container.clientService,
                sessionStore = container.sessionStore,
                wooCommerceService = container.wooCommerceService,
                evidenceDraftStore = container.evidenceDraftStore,
                orderConfirmationStore = container.orderConfirmationStore,
                orderEvidenceGenerator = container.orderEvidenceGenerator,
                locationProvider = container.locationProvider,
            )
        }
    }

    fun camera(container: AppContainer, clientId: String): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CameraViewModel(
                clientId = clientId,
                cameraController = container.createCameraController(),
                evidenceStamper = container.evidenceStamper,
                evidenceDraftStore = container.evidenceDraftStore,
                evidenceFileStore = container.evidenceFileStore,
                locationProvider = container.locationProvider,
                timeProvider = container.timeProvider,
                clientService = container.clientService,
                appPreferencesStore = container.appPreferencesStore,
            )
        }
    }

    fun shiftSummary(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ShiftSummaryViewModel(container.shiftService, container.timeProvider, container.appPreferencesStore) }
    }

    fun interDayShift(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { InterDayShiftViewModel(container.routePlanService, container.timeProvider) }
    }

    fun update(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { UpdateViewModel(container.updateService, container.apkInstaller) }
    }
}
