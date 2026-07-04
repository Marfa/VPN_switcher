package dev.themarfa.vpnswitcher;

interface IUserService {
    void destroy() = 16777114;
    int execCommand(in String[] command) = 1;
    String execCommandWithOutput(in String[] command) = 2;
}
