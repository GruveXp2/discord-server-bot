package org.gruve;

public enum ServerStatus {

    INITIALIZING, // discordbotten skrus på
    OFFLINE, // serveren er av
    LOADING, // serveren skal starte
    FILE_BUG, // hvis den har vært i starting for lenge så er det no galt med fila
    STARTING, // serveren holder på å starte
    PLUGIN_BUG, // serveren har vært i loading for lenge
    ONLINE, // serveren er oppe
    VPN, // har vpn så ingen kan koble til
    CLOSING, // serveren er i ferd med å lukkes
    TIMEOUT, // serveren er deaktivert i en periode
    ERROR // no ant galt har skjedd
}
