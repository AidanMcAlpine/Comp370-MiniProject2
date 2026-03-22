package mini_project_01;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class FailoverManager {
    private ElectionAlgorithm electionAlgorithm;

    public FailoverManager() {
        this.electionAlgorithm = new LowestIdElection();
    }

    // Elects a new primary from the list of backups/associated addresses
    public int initiateFailover(HashMap<Integer, Map.Entry<String, Integer>> backups) {
        int chosenPrimary = electionAlgorithm.electPrimary(new ArrayList<>(backups.keySet()));
        if (chosenPrimary == -1) {
            return -1;
        }
        System.out.println("Electing server " + chosenPrimary + " as primary");
        Map.Entry<String, Integer> address = backups.get(chosenPrimary);
        try (
            Socket promoteSender = new Socket(address.getKey(), address.getValue());
            DataOutputStream out = new DataOutputStream(promoteSender.getOutputStream());
        ) {
            out.write(new JsonMessageSerializer().serialize(new Message("PROMOTE", 0, "PRIMARY")));
        } catch (IOException e) {
            System.out.println("Failed to promote new primary, retrying");
            backups.remove(chosenPrimary);
            return initiateFailover(backups);
        } catch (Exception e) {
            System.out.println("Failed to serialize promote message (somehow), retrying");
            return initiateFailover(backups);
        }
        return chosenPrimary;
    }
}

interface ElectionAlgorithm {
    int electPrimary(ArrayList<Integer> backups);
}

class LowestIdElection implements ElectionAlgorithm {
    @Override
    public int electPrimary(ArrayList<Integer> backups) {
        return backups.stream().min(Integer::compare).orElse(-1);
    }
}