import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AdvancedTrafficSimulation extends JPanel implements ActionListener {

    static class Node {
        String id;
        int x, y;
        boolean hasTrafficLight;
        boolean isGreen = true;
        long lastLightChange = System.currentTimeMillis();
        long lightInterval = 4000; // Смена раз в 4 секунды

        public Node(String id, int x, int y, boolean hasTrafficLight) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.hasTrafficLight = hasTrafficLight;
        }

        public void update() {
            if (hasTrafficLight && (System.currentTimeMillis() - lastLightChange > lightInterval)) {
                isGreen = !isGreen;
                lastLightChange = System.currentTimeMillis();
            }
        }
    }

    static class Road {
        Node from;
        Node to;
        int lanesCount;
        List<Car> carsOnRoad = new ArrayList<>();

        public Road(Node from, Node to, int lanesCount) {
            this.from = from;
            this.to = to;
            this.lanesCount = lanesCount;
        }
    }

    static class Car {
        String id;
        Road currentRoad;
        int laneIndex;         // Текущая целевая полоса
        double distanceTravelled;
        double currentSpeed = 0;
        double desiredSpeed;   // Желаемая скорость (у всех разная)
        Color color;

        // Параметры для плавного перестроения
        double visualLaneOffset; // Текущее визуальное положение между полосами
        boolean isChangingLane = false;

        // Константы безопасности
        double safeDistance = 35.0; // Безопасная дистанция в пикселях

        public Car(String id, Road startRoad, int laneIndex, double desiredSpeed) {
            this.id = id;
            this.currentRoad = startRoad;
            this.laneIndex = laneIndex;
            this.visualLaneOffset = laneIndex; // Изначально ровно на своей полосе
            this.desiredSpeed = desiredSpeed;
            this.currentRoad.carsOnRoad.add(this);

            Random rand = new Random();
            this.color = new Color(rand.nextInt(200), rand.nextInt(200), rand.nextInt(255));
        }

        public void update(List<Car> allCars) {
            double roadLength = getLength();
            double distanceToEnd = roadLength - distanceTravelled;

            // 1. Плавная анимация смены полосы (стремимся к целевому laneIndex)
            if (visualLaneOffset != laneIndex) {
                isChangingLane = true;
                if (visualLaneOffset < laneIndex) {
                    visualLaneOffset = Math.min(laneIndex, visualLaneOffset + 0.08);
                } else {
                    visualLaneOffset = Math.max(laneIndex, visualLaneOffset - 0.08);
                }
            } else {
                isChangingLane = false;
            }

            // 2. Поиск машины впереди на нашей полосе
            Car leader = null;
            double minDistanceToLeader = Double.MAX_VALUE;

            for (Car other : currentRoad.carsOnRoad) {
                if (other == this) continue;
                // Проверяем ту же полосу (или целевую полосу, если мы/он в процессе перестроения)
                if (other.laneIndex == this.laneIndex) {
                    if (other.distanceTravelled > this.distanceTravelled) {
                        double dist = other.distanceTravelled - this.distanceTravelled;
                        if (dist < minDistanceToLeader) {
                            minDistanceToLeader = dist;
                            leader = other;
                        }
                    }
                }
            }

            // 3. Вычисление целевой скорости на основе ИИ (Интеллектуальная модель водителя)
            double targetSpeed = desiredSpeed;

            // Препятствие 1: Светофор
            if (distanceToEnd < 50 && currentRoad.to.hasTrafficLight && !currentRoad.to.isGreen) {
                // Плавный расчет скорости до полной остановки у стоп-линии
                targetSpeed = Math.max(0, (distanceToEnd - 15) / 10.0);
            }

            // Препятствие 2: Впереди идущая машина (лидер)
            if (leader != null && minDistanceToLeader < safeDistance * 2) {
                if (minDistanceToLeader <= safeDistance) {
                    // Держим скорость лидера или экстренно тормозим, если слишком близко
                    targetSpeed = Math.min(targetSpeed, leader.currentSpeed * (minDistanceToLeader / safeDistance));
                } else {
                    // Адаптируем скорость под лидера заранее
                    targetSpeed = Math.min(targetSpeed, leader.currentSpeed);
                }

                // Логика ОБГОНА: если скорость из-за лидера падает, пробуем перестроиться
                if (currentSpeed < desiredSpeed * 0.9 && !isChangingLane) {
                    tryToChangeLane();
                }
            }

            // Применяем ускорение/торможение
            if (currentSpeed < targetSpeed) {
                currentSpeed = Math.min(targetSpeed, currentSpeed + 0.15); // Разгон
            } else {
                currentSpeed = Math.max(targetSpeed, currentSpeed - 0.25); // Торможение
            }

            distanceTravelled += currentSpeed;

            // 4. Переход на следующую дорогу
            if (distanceTravelled >= roadLength) {
                distanceTravelled = 0;
                currentRoad.carsOnRoad.remove(this);

                // В нашей кольцевой системе просто ищем следующую дорогу по цепочке
                int nextIndex = (allSimRoads.indexOf(currentRoad) + 1) % allSimRoads.size();
                currentRoad = allSimRoads.get(nextIndex);
                currentRoad.carsOnRoad.add(this);
                // Сбрасываем визуальные параметры для новой дороги
                visualLaneOffset = laneIndex;
            }
        }

        private void tryToChangeLane() {
            int targetLane = (laneIndex == 0) ? 1 : 0; // Ищем свободную полосу (всего 2 полосы)

            // Проверяем, безопасна ли смена полосы (нет ли машин сзади и спереди в зоне маневра)
            boolean laneFree = true;
            for (Car other : currentRoad.carsOnRoad) {
                if (other.laneIndex == targetLane) {
                    // Расстояние между нами по оси дороги
                    double spatialDist = Math.abs(other.distanceTravelled - this.distanceTravelled);
                    if (spatialDist < safeDistance) {
                        laneFree = false; // Слишком близко, перестраиваться нельзя
                        break;
                    }
                }
            }

            if (laneFree) {
                this.laneIndex = targetLane; // Меняем целевую полосу!
            }
        }

        public double getLength() {
            return Math.hypot(currentRoad.to.x - currentRoad.from.x, currentRoad.to.y - currentRoad.from.y);
        }

        public Point getCoordinates() {
            double roadLength = getLength();
            double ratio = distanceTravelled / roadLength;

            double baseX = currentRoad.from.x + (currentRoad.to.x - currentRoad.from.x) * ratio;
            double baseY = currentRoad.from.y + (currentRoad.to.y - currentRoad.from.y) * ratio;

            double dx = (currentRoad.to.x - currentRoad.from.x) / roadLength;
            double dy = (currentRoad.to.y - currentRoad.from.y) / roadLength;

            double nx = -dy;
            double ny = dx;

            double laneWidth = 16;
            // Используем visualLaneOffset вместо дискретного laneIndex для плавного сдвига
            double offset = (visualLaneOffset - (currentRoad.lanesCount - 1) / 2.0) * laneWidth;

            return new Point((int) (baseX + nx * offset), (int) (baseY + ny * offset));
        }
    }

    // Статическое поле для простого связывания путей машин в кольцо
    private static List<Road> allSimRoads = new ArrayList<>();

    private final List<Node> nodes = new ArrayList<>();
    private final List<Car> cars = new ArrayList<>();

    public AdvancedTrafficSimulation() {
        initSimulationWorld();
        Timer timer = new Timer(30, this);
        timer.start();
    }

    private void initSimulationWorld() {
        Node n1 = new Node("A", 100, 100, false);
        Node n2 = new Node("B", 700, 100, true);  // Светофор 1
        Node n3 = new Node("C", 700, 550, false);
        Node n4 = new Node("D", 100, 550, true);  // Светофор 2

        nodes.add(n1); nodes.add(n2); nodes.add(n3); nodes.add(n4);

        Road r1 = new Road(n1, n2, 2);
        Road r2 = new Road(n2, n3, 2);
        Road r3 = new Road(n3, n4, 2);
        Road r4 = new Road(n4, n1, 2);

        allSimRoads.add(r1); allSimRoads.add(r2); allSimRoads.add(r3); allSimRoads.add(r4);

        // Добавляем машины с разными скоростями
        // Медленный грузовик (желаемая скорость 1.2 пикселя/кадр)
        cars.add(new Car("Truck", r1, 0, 1.2));
        cars.get(0).distanceTravelled = 250;

        // Быстрые легковушки (желаемая скорость 3.0 и 3.5), которые будут его догонять и обгонять
        cars.add(new Car("Car-1", r1, 0, 3.2));
        cars.add(new Car("Car-2", r1, 1, 3.5)); // Стартует на соседней полосе

        // Добавим машин на другие участки для создания плотности трафика
        cars.add(new Car("Car-3", r3, 0, 2.8));
        cars.add(new Car("Car-4", r3, 0, 1.5)); // Опять медленная машина на нижней дороге
        cars.get(4).distanceTravelled = 200;
        cars.add(new Car("Car-5", r3, 0, 3.0));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Node node : nodes) node.update();
        for (Car car : cars) car.update(cars);
      //  Используйте код с осторожностью.
        repaint();
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.fillRect(0, 0, getWidth(), getHeight());
// Рисуем дороги
        for (Road road : allSimRoads) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.setStroke(new BasicStroke(36));
            g2d.drawLine(road.from.x, road.from.y, road.to.x, road.to.y);
// Разметка полос
            g2d.setColor(Color.WHITE);
            float[] dash = {12.0f, 12.0f};
            g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
            g2d.drawLine(road.from.x, road.from.y, road.to.x, road.to.y);
        }
// Перекрестки и светофоры
        for (Node node : nodes) {
            g2d.setColor(Color.GRAY);
            g2d.fillOval(node.x - 22, node.y - 22, 44, 44);
            if (node.hasTrafficLight) {
                g2d.setColor(node.isGreen ? Color.GREEN : Color.RED);
                g2d.fillRect(node.x - 8, node.y - 45, 16, 16);
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawRect(node.x - 8, node.y - 45, 16, 16);
            }
        }
// Рисуем машины
        for (Car car : cars) {
            Point p = car.getCoordinates();
            g2d.setColor(car.color);
            g2d.fillOval(p.x - 7, p.y - 7, 14, 14);
// Если это грузовик (медленный), сделаем его квадратным для наглядности обгона
            if (car.desiredSpeed < 2.0) {
                g2d.fillRect(p.x - 8, p.y - 8, 16, 16);
            }
        }
    }
    public static void main(String[] args) {
        JFrame frame = new JFrame("Симуляция: Обгоны и соблюдение дистанции");
        AdvancedTrafficSimulation simulationPanel = new AdvancedTrafficSimulation();
        frame.add(simulationPanel);
        frame.setSize(800, 680);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

//### Разбор ключевых нововведений:
//
//        1. **Интеллектуальное торможение (`targetSpeed`)**:
//        * Машина постоянно проверяет массив `carsOnRoad` и вычисляет расстояние до ближайшего автомобиля **впереди на своей полосе**.
//        * При приближении к лидеру ближе чем на `safeDistance * 2` (70 пикселей) скорость плавно снижается до скорости лидера. Если дистанция сокращается до критической, машина синхронизирует скорость или останавливается, выстраивая **аккуратную цепочку (очередь)** перед светофором.
//        2. **Алгоритм принятия решения об обгоне (`tryToChangeLane`)**:
//        * Если скорость машины падает ниже её комфортной (`desiredSpeed`), срабатывает триггер обгона.
//        * Машина сканирует соседнюю полосу (проверяет «слепую зону» сзади себя и пространство впереди). Если на соседней полосе в радиусе `safeDistance` никого нет, она переключает свой логический `laneIndex`.
//        3. **Плавная визуализация перестроения (`visualLaneOffset`)**:
//        * Вместо мгновенного телепортирования на соседнюю полосу, в каждом кадре переменная `visualLaneOffset` плавно шагает в сторону целевого индекса полосы (`+0.08` или `-0.08`). Математический вектор смещения подхватывает это дробное значение, и круг на экране плавно смещается по диагонали в сторону новой полосы.
//
//<FollowUp>
//Если архитектура понятна и вы хотите двигаться дальше, сообщите:
//        * Нужно ли добавить **сбор статистики** (например, график средней скорости, счетчик проехавших машин)?
//        * Требуется ли спроектировать **полноценный перекресток двух дорог** (крест-накрест), где светофор переключает направления (пока горит зеленый для горизонтальной дороги — для вертикальной горит красный)?
//</FollowUp>
