package pt.ulisboa.tecnico.hdsledger.service.models;

public record Pair<T1, T2>(T1 first, T2 second) {

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Pair other = (Pair) obj;
    if (first == null) {
      if (other.first != null)
        return false;
    } else if (!first.equals(other.first))
      return false;
    if (second == null) {
      return other.second == null;
    } else return second.equals(other.second);
  }

}
